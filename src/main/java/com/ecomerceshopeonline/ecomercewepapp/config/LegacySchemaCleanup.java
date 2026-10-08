package com.ecomerceshopeonline.ecomercewepapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Drops constraints that {@code ddl-auto=update} adds but never removes.
 * <p>
 * {@code uk_cart_items_cart_product} allowed one cart line per product. With product variants
 * a cart may hold the same product twice (two different options), so it was replaced by
 * {@code uk_cart_items_cart_product_variant}; an existing database still carries the old one.
 * <p>
 * Postgres treats NULLs as distinct in a unique constraint, so the composite constraint does
 * not stop two lines for the same <em>plain</em> product ({@code variant_id IS NULL}) in one
 * cart. A partial unique index restores that guarantee; JPA cannot declare it, and H2 has no
 * partial indexes, so it is only created on PostgreSQL. Duplicate plain lines left over from
 * before the index are merged (quantities summed into the lowest id) so it can be created.
 * Idempotent and harmless on a fresh schema.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacySchemaCleanup implements ApplicationRunner {

    private static final String[] STATEMENTS = {
            "ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS uk_cart_items_cart_product"
    };

    private static final String PLAIN_LINE_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_cart_items_cart_product_plain"
                    + " ON cart_items (cart_id, product_id) WHERE variant_id IS NULL";

    /** Duplicate plain lines (same cart and product, no variant) that block the index. */
    private static final String FIND_DUPLICATE_PLAIN_LINES =
            "SELECT cart_id, product_id, COUNT(*) AS line_count, SUM(quantity) AS total, MIN(id) AS keep_id"
                    + " FROM cart_items WHERE variant_id IS NULL"
                    + " GROUP BY cart_id, product_id HAVING COUNT(*) > 1";

    /** The lowest id of each duplicate group takes the summed quantity... */
    private static final String MERGE_DUPLICATE_PLAIN_LINES =
            "UPDATE cart_items c SET quantity = d.total"
                    + " FROM (SELECT MIN(id) AS keep_id, SUM(quantity) AS total FROM cart_items"
                    + "       WHERE variant_id IS NULL GROUP BY cart_id, product_id HAVING COUNT(*) > 1) d"
                    + " WHERE c.id = d.keep_id";

    /** ...and the other lines of the group go. */
    private static final String DELETE_DUPLICATE_PLAIN_LINES =
            "DELETE FROM cart_items c USING cart_items k"
                    + " WHERE c.variant_id IS NULL AND k.variant_id IS NULL"
                    + " AND c.cart_id = k.cart_id AND c.product_id = k.product_id AND c.id > k.id";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        executeAll(STATEMENTS);
        if (isPostgres()) {
            createPlainLineIndex();
        }
    }

    /**
     * Creates the partial unique index. If existing duplicate plain lines block it, they are
     * merged first (quantities summed into the lowest id, the rest deleted) in one transaction
     * together with the index creation, then the index is created.
     */
    private void createPlainLineIndex() {
        try {
            jdbcTemplate.execute(PLAIN_LINE_INDEX);
            return;
        } catch (DataAccessException ex) {
            log.warn("Could not create the plain cart line index ({}), merging duplicate lines first",
                    ex.getMessage());
        }
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                List<Map<String, Object>> groups = jdbcTemplate.queryForList(FIND_DUPLICATE_PLAIN_LINES);
                for (Map<String, Object> group : groups) {
                    log.warn("Merging {} duplicate plain cart lines for cart {} / product {} into line {}"
                                    + " (quantity {})",
                            group.get("line_count"), group.get("cart_id"), group.get("product_id"),
                            group.get("keep_id"), group.get("total"));
                }
                int merged = jdbcTemplate.update(MERGE_DUPLICATE_PLAIN_LINES);
                int deleted = jdbcTemplate.update(DELETE_DUPLICATE_PLAIN_LINES);
                jdbcTemplate.execute(PLAIN_LINE_INDEX);
                log.info("Merged {} duplicate plain cart line group(s), deleted {} line(s),"
                        + " created uk_cart_items_cart_product_plain", merged, deleted);
            });
        } catch (RuntimeException ex) {
            log.warn("Schema cleanup skipped ({}): {}", PLAIN_LINE_INDEX, ex.getMessage());
        }
    }

    private void executeAll(String[] statements) {
        for (String statement : statements) {
            try {
                jdbcTemplate.execute(statement);
            } catch (DataAccessException ex) {
                log.warn("Schema cleanup skipped ({}): {}", statement, ex.getMessage());
            }
        }
    }

    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            return product != null && product.toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (DataAccessException ex) {
            log.warn("Could not determine the database product: {}", ex.getMessage());
            return false;
        }
    }
}
