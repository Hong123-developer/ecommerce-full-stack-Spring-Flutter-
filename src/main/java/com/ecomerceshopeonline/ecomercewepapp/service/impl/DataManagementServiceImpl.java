package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DataGroup;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DataSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.FieldValidationException;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.DataManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Backup, restore and bulk delete, in plain JDBC over a fixed list of tables. Going through
 * the tables rather than the entities means a backup holds every column - including ones a
 * later version adds - and a restore puts rows back with their original ids, so every link
 * between them (an order's customer, an item's product) survives the round trip.
 * <p>
 * Restore and delete each run in one transaction: they succeed completely or change nothing.
 * Generated-id counters only ever move forward, so an id that once belonged to someone is
 * never handed to someone else (an access token names its user by id).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataManagementServiceImpl implements DataManagementService {

    static final String FORMAT = "ecomercewepapp-backup";
    static final int VERSION = 1;

    private static final String USER_ROLES = "user_roles";

    /** Every table in a backup, parents first: the insert order of a restore. Wipes run in reverse. */
    static final List<String> BACKUP_TABLES = List.of(
            "store_settings", "payment_account",
            "categories", "products", "product_gallery_images", "product_option_names",
            "product_variants", "product_variant_options",
            "delivery_options", "hero_slides",
            "users", USER_ROLES, "addresses", "carts", "cart_items",
            "orders", "order_items", "payments", "notifications", "product_reviews");

    /** Tables with a generated id. */
    private static final List<String> IDENTITY_TABLES = List.of(
            "categories", "products", "product_variants", "delivery_options", "hero_slides",
            "users", "addresses", "carts", "cart_items", "orders", "order_items", "payments", "notifications",
            "product_reviews");

    /**
     * Re-derives every product's rating from its visible reviews (as ProductReviewService does
     * for one product), after reviews were removed here in bulk.
     */
    private static final String RECOMPUTE_RATINGS = """
            UPDATE products SET
              rating_count = (SELECT COUNT(*) FROM product_reviews r
                              WHERE r.product_id = products.id AND r.visible = TRUE),
              rating_average = COALESCE((SELECT ROUND(AVG(r.rating * 1.0), 1) FROM product_reviews r
                                         WHERE r.product_id = products.id AND r.visible = TRUE), 0)""";

    /** One-row tables with an assigned id. */
    private static final List<String> SINGLETONS = List.of("store_settings", "payment_account");

    /** Sign-in state: never in a backup; a restore clears it, so everyone signs in again. */
    private static final List<String> SESSION_TABLES = List.of("refresh_tokens", "verification_codes");

    /** Accounts without the admin role. */
    private static final String CUSTOMER_IDS = """
            SELECT u.id FROM users u WHERE NOT EXISTS (
                SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id AND r.name = 'ROLE_ADMIN')""";

    private static final int CHUNK = 500;

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private volatile Boolean postgres;

    // ---------------------------------------------------------------- summary

    @Override
    @Transactional(readOnly = true)
    public DataSummaryResponse summary() {
        return new DataSummaryResponse(
                count("SELECT COUNT(*) FROM orders"),
                count("SELECT COUNT(*) FROM payments"),
                count("SELECT COUNT(*) FROM notifications"),
                count("SELECT COUNT(*) FROM (" + CUSTOMER_IDS + ") c"),
                count("SELECT COUNT(DISTINCT o.user_id) FROM orders o WHERE o.user_id IN (" + CUSTOMER_IDS + ")"),
                count("SELECT COUNT(*) FROM products"),
                count("SELECT COUNT(*) FROM categories"),
                count("SELECT COUNT(*) FROM hero_slides"),
                count("SELECT COUNT(*) FROM delivery_options"),
                count("SELECT COUNT(*) FROM store_settings") > 0,
                count("""
                        SELECT COUNT(DISTINCT ur.user_id) FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                        WHERE r.name = 'ROLE_ADMIN'"""));
    }

    // ---------------------------------------------------------------- backup

    /** REPEATABLE READ: every table is read from the same snapshot, so the rows fit together. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> backup(Long adminId, String password) {
        requirePassword(adminId, password);

        Map<String, Object> tables = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            List<Map<String, Object>> rows = USER_ROLES.equals(table) ? exportUserRoles() : exportTable(table);
            tables.put(table, rows);
            counts.put(table, rows.size());
        }

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("format", FORMAT);
        backup.put("version", VERSION);
        backup.put("createdAt", LocalDateTime.now().withNano(0).toString());
        backup.put("counts", counts);
        backup.put("tables", tables);
        log.info("Admin {} downloaded a backup: {}", adminId, counts);
        return backup;
    }

    private List<Map<String, Object>> exportTable(String table) {
        String order = IDENTITY_TABLES.contains(table) || SINGLETONS.contains(table) ? " ORDER BY id" : "";
        return jdbc.query("SELECT * FROM " + table + order, (rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                row.put(meta.getColumnLabel(c).toLowerCase(Locale.ROOT), toJson(rs.getObject(c)));
            }
            return row;
        });
    }

    /** By role name, not role id: ids of the seeded roles may differ between databases. */
    private List<Map<String, Object>> exportUserRoles() {
        return jdbc.query("""
                SELECT ur.user_id, r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                ORDER BY ur.user_id""", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", rs.getLong(1));
            row.put("role_name", rs.getString(2));
            return row;
        });
    }

    /** A JDBC value as JSON: exact decimals and timestamps as text, binary as base64. */
    private static Object toJson(Object value) throws SQLException {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (value instanceof Blob blob) {
            return Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length()));
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value.toString();
    }

    // ---------------------------------------------------------------- restore

    @Override
    @Transactional
    public Map<String, Integer> restore(Long adminId, String password, String confirm, Map<String, Object> backup) {
        requirePassword(adminId, password);
        requireWord(confirm, "RESTORE");
        Map<String, List<Map<String, Object>>> tables = readBackup(backup);
        requireActiveAdmin(tables);

        List<String> wipeOrder = wipeOrder();
        // ACCESS EXCLUSIVE also waits for requests that already read these tables: one that read a
        // row before the restore must not write its old version back over the restored row after.
        lockTables(wipeOrder, "ACCESS EXCLUSIVE");
        Map<String, Long> floor = new HashMap<>();
        for (String table : IDENTITY_TABLES) {
            floor.put(table, nextId(table));
        }

        Map<String, Integer> restored = new LinkedHashMap<>();
        try {
            for (String table : wipeOrder) {
                jdbc.update("DELETE FROM " + table);
            }
            for (String table : BACKUP_TABLES) {
                restored.put(table, insert(table, tables.get(table)));
            }
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("This backup file doesn't fit together, so nothing was changed. ("
                    + shortReason(e) + ")");
        } catch (DateTimeParseException | IllegalArgumentException | ArithmeticException e) {
            throw new BadRequestException("This backup file has a value that can't be read, so nothing was "
                    + "changed. (" + e.getMessage() + ")");
        }

        for (String table : IDENTITY_TABLES) {
            long backupMax = tables.get(table).stream()
                    .map(row -> asLong(row.get("id")))
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max().orElse(0);
            restartIds(table, Math.max(floor.get(table), backupMax + 1));
        }
        log.warn("Admin {} restored a backup made {}: {}", adminId, backup.get("createdAt"), restored);
        return restored;
    }

    /** Children first: notifications ... users ... categories, store settings; sign-in state before users. */
    private static List<String> wipeOrder() {
        List<String> order = new ArrayList<>(BACKUP_TABLES);
        Collections.reverse(order);
        order.addAll(order.indexOf(USER_ROLES), SESSION_TABLES);
        return order;
    }

    /** The file's format, version and table arrays; a table it lacks is restored empty. */
    private static Map<String, List<Map<String, Object>>> readBackup(Map<String, Object> backup) {
        if (!FORMAT.equals(backup.get("format"))) {
            throw new BadRequestException("This file is not a backup from this shop");
        }
        Long version = asLong(backup.get("version"));
        if (version == null || version < 1) {
            throw new BadRequestException("This backup file is damaged (no version)");
        }
        if (version > VERSION) {
            throw new BadRequestException("This backup was made by a newer version of the shop and can't be restored here");
        }
        if (!(backup.get("tables") instanceof Map<?, ?> raw)) {
            throw new BadRequestException("This backup file is damaged (no tables)");
        }
        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            Object rows = raw.get(table);
            if (rows == null) {
                tables.put(table, List.of());
                continue;
            }
            if (!(rows instanceof List<?> list)) {
                throw new BadRequestException("This backup file is damaged (" + table + ")");
            }
            List<Map<String, Object>> typed = new ArrayList<>(list.size());
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> map)) {
                    throw new BadRequestException("This backup file is damaged (" + table + ")");
                }
                Map<String, Object> lower = new LinkedHashMap<>();
                map.forEach((key, value) -> lower.put(String.valueOf(key).toLowerCase(Locale.ROOT), value));
                typed.add(lower);
            }
            tables.put(table, typed);
        }
        return tables;
    }

    /** A backup without an enabled admin would lock everyone out of the admin pages. */
    private static void requireActiveAdmin(Map<String, List<Map<String, Object>>> tables) {
        Set<Long> adminIds = new HashSet<>();
        for (Map<String, Object> link : tables.get(USER_ROLES)) {
            if ("ROLE_ADMIN".equals(String.valueOf(link.get("role_name")))) {
                adminIds.add(asLong(link.get("user_id")));
            }
        }
        boolean active = tables.get("users").stream()
                .anyMatch(user -> adminIds.contains(asLong(user.get("id"))) && asBoolean(user.get("enabled")));
        if (!active) {
            throw new BadRequestException("This backup has no active administrator account, so restoring it "
                    + "would lock everyone out of the admin. Nothing was changed.");
        }
    }

    private int insert(String table, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        if (USER_ROLES.equals(table)) {
            return insertUserRoles(rows);
        }
        Set<String> present = new LinkedHashSet<>();
        rows.forEach(row -> present.addAll(row.keySet()));
        // Only columns both the file and this database have: a column the file lacks takes its default.
        List<Column> columns = columnsOf(table).stream()
                .filter(column -> present.contains(column.key()))
                .toList();
        if (columns.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder marks = new StringBuilder();
        int[] types = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append('"').append(columns.get(i).name()).append('"');
            marks.append(i == 0 ? "?" : ", ?");
            types[i] = columns.get(i).bindType();
        }
        sql.append(") VALUES (").append(marks).append(')');

        List<Object[]> args = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = fromJson(row.get(columns.get(i).key()), columns.get(i).type());
            }
            args.add(values);
        }
        for (int from = 0; from < args.size(); from += CHUNK) {
            jdbc.batchUpdate(sql.toString(), args.subList(from, Math.min(args.size(), from + CHUNK)), types);
        }
        return rows.size();
    }

    private int insertUserRoles(List<Map<String, Object>> rows) {
        Map<String, Long> roleIds = new HashMap<>();
        jdbc.query("SELECT id, name FROM roles", rs -> {
            roleIds.put(rs.getString(2), rs.getLong(1));
        });
        List<Object[]> args = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long roleId = roleIds.get(String.valueOf(row.get("role_name")));
            Long userId = asLong(row.get("user_id"));
            if (roleId != null && userId != null) {
                args.add(new Object[]{userId, roleId});
            }
        }
        if (!args.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", args,
                    new int[]{Types.BIGINT, Types.BIGINT});
        }
        return args.size();
    }

    /** One column as this database names and types it. */
    private record Column(String name, int type, String typeName) {

        String key() {
            return name.toLowerCase(Locale.ROOT);
        }

        /** H2 reports its ENUM columns as OTHER, which would bind a Java object; text is what they take. */
        int bindType() {
            return type == Types.OTHER && "ENUM".equalsIgnoreCase(typeName) ? Types.VARCHAR : type;
        }
    }

    private List<Column> columnsOf(String table) {
        return jdbc.query("SELECT * FROM " + table + " WHERE 1 = 0", rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            List<Column> columns = new ArrayList<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                columns.add(new Column(meta.getColumnName(c), meta.getColumnType(c), meta.getColumnTypeName(c)));
            }
            return columns;
        });
    }

    /** JSON back to what the column holds (the reverse of {@link #toJson}). */
    private static Object fromJson(Object value, int sqlType) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return switch (sqlType) {
            case Types.BIGINT -> asLong(value);
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Math.toIntExact(asLong(value));
            case Types.NUMERIC, Types.DECIMAL -> new BigDecimal(text);
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> Double.valueOf(text);
            case Types.BOOLEAN, Types.BIT -> asBoolean(value);
            case Types.TIMESTAMP -> Timestamp.valueOf(LocalDateTime.parse(text));
            case Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.parse(text);
            case Types.DATE -> java.sql.Date.valueOf(LocalDate.parse(text));
            case Types.TIME -> Time.valueOf(LocalTime.parse(text));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> Base64.getDecoder().decode(text);
            default -> text;
        };
    }

    // ---------------------------------------------------------------- id counters

    /** The next id this table would hand out right now (never less than max + 1). */
    private long nextId(String table) {
        long next = count("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table);
        if (isPostgres()) {
            String sequence = jdbc.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String.class, table);
            if (sequence != null) {
                Map<String, Object> state = jdbc.queryForMap("SELECT last_value, is_called FROM " + sequence);
                long last = ((Number) state.get("last_value")).longValue();
                next = Math.max(next, Boolean.TRUE.equals(state.get("is_called")) ? last + 1 : last);
            }
        }
        return next;
    }

    private void restartIds(String table, long next) {
        if (isPostgres()) {
            String sequence = jdbc.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String.class, table);
            if (sequence != null) {
                jdbc.queryForObject("SELECT setval(CAST(? AS regclass), ?, false)", Long.class, sequence, next);
            }
        } else {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + next);
        }
    }

    // ---------------------------------------------------------------- delete

    @Override
    @Transactional
    public Map<String, Integer> delete(Long adminId, String password, String confirm, Set<DataGroup> groups) {
        requirePassword(adminId, password);
        requireWord(confirm, "DELETE");
        if (groups == null || groups.isEmpty()) {
            throw new FieldValidationException("groups", "Choose what to delete");
        }
        boolean orders = groups.contains(DataGroup.ORDERS);
        boolean customers = groups.contains(DataGroup.CUSTOMERS);
        boolean catalog = groups.contains(DataGroup.CATALOG);
        boolean content = groups.contains(DataGroup.CONTENT);
        if (customers && !orders
                && count("SELECT COUNT(*) FROM orders WHERE user_id IN (" + CUSTOMER_IDS + ")") > 0) {
            throw new BadRequestException("Some customers have orders. Tick \"Orders & payments\" as well, "
                    + "or keep the customers.");
        }

        // Deleted rows are gone, so a late write from a request in flight fails instead of overwriting.
        lockTables(wipeOrder(), "SHARE ROW EXCLUSIVE");
        Map<String, Integer> done = new LinkedHashMap<>();
        if (orders) {
            for (String table : List.of("notifications", "payments", "order_items", "orders")) {
                done.merge(table, jdbc.update("DELETE FROM " + table), Integer::sum);
            }
        }
        if (catalog) {
            if (!orders) {
                // Kept orders keep their item snapshots (name, SKU, price); only the links go.
                done.put("order_items_unlinked", jdbc.update("""
                        UPDATE order_items SET product_id = NULL, variant_id = NULL
                        WHERE product_id IS NOT NULL OR variant_id IS NOT NULL"""));
            }
            for (String table : List.of("product_reviews", "cart_items", "product_variant_options", "product_variants",
                    "product_option_names", "product_gallery_images", "products", "categories")) {
                done.merge(table, jdbc.update("DELETE FROM " + table), Integer::sum);
            }
        }
        if (content) {
            if (!orders) {
                done.put("orders_unlinked", jdbc.update(
                        "UPDATE orders SET delivery_option_id = NULL WHERE delivery_option_id IS NOT NULL"));
            }
            for (String table : List.of("hero_slides", "delivery_options")) {
                done.merge(table, jdbc.update("DELETE FROM " + table), Integer::sum);
            }
        }
        if (customers) {
            deleteCustomers(done);
            if (!catalog && done.getOrDefault("product_reviews", 0) > 0) {
                // Their reviews went with them, so the kept products must stop counting them.
                jdbc.update(RECOMPUTE_RATINGS);
            }
        }
        if (groups.contains(DataGroup.SETTINGS)) {
            for (String table : SINGLETONS) {
                done.merge(table, jdbc.update("DELETE FROM " + table), Integer::sum);
            }
        }
        log.warn("Admin {} deleted {}: {}", adminId, groups, done);
        return done;
    }

    /** Every non-admin account and everything hanging off it. */
    private void deleteCustomers(Map<String, Integer> done) {
        List<Long> ids = jdbc.queryForList(CUSTOMER_IDS, Long.class);
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + CHUNK));
            String in = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            Object[] args = chunk.toArray();
            done.merge("cart_items", jdbc.update(
                    "DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (" + in + "))",
                    args), Integer::sum);
            for (String table : List.of("carts", "addresses", "refresh_tokens", "verification_codes",
                    "notifications", "payments", "product_reviews", USER_ROLES)) {
                done.merge(table, jdbc.update("DELETE FROM " + table + " WHERE user_id IN (" + in + ")", args),
                        Integer::sum);
            }
            done.merge("users", jdbc.update("DELETE FROM users WHERE id IN (" + in + ")", args), Integer::sum);
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * PostgreSQL: hold off other transactions (checkouts, the expiry jobs) until this commits.
     * Gives up after a while rather than queueing behind a long transaction.
     *
     * @param mode SHARE ROW EXCLUSIVE lets reads go on; ACCESS EXCLUSIVE blocks them too
     */
    private void lockTables(List<String> tables, String mode) {
        if (!isPostgres()) {
            return;
        }
        try {
            jdbc.execute("SET LOCAL lock_timeout = '15s'");
            jdbc.execute("LOCK TABLE " + String.join(", ", tables) + " IN " + mode + " MODE");
        } catch (DataAccessException e) {
            throw new ConflictException("The shop is busy right now. Please try again in a moment.");
        }
    }

    private boolean isPostgres() {
        Boolean known = postgres;
        if (known == null) {
            String product = jdbc.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            known = product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
            postgres = known;
        }
        return known;
    }

    private void requirePassword(Long adminId, String password) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BadRequestException("Your account no longer exists. Please sign in again."));
        if (admin.getPassword() == null) {
            throw new FieldValidationException("password",
                    "Your account has no password (you sign in with Google). Set a password first.");
        }
        if (password == null || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new FieldValidationException("password", "Incorrect password");
        }
    }

    private static void requireWord(String confirm, String word) {
        if (confirm == null || !word.equals(confirm.trim().toUpperCase(Locale.ROOT))) {
            throw new FieldValidationException("confirm", "Type " + word + " to confirm");
        }
    }

    private long count(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString().trim());
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString().trim());
    }

    private static String shortReason(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return "a link between records is broken";
        }
        message = message.lines().findFirst().orElse(message);
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
