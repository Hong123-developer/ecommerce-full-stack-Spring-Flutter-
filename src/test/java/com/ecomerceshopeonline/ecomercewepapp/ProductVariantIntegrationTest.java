package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductVariantRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Product options and variants (Model / Storage / Color pickers): admin CRUD with the
 * derived product price and stock, public visibility, cart lines per variant, checkout
 * snapshots and stock, cancellation - and that plain products behave exactly as before.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:variant_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class ProductVariantIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String DOT = " · ";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private RequestPostProcessor asCustomer;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");
    private long categoryId;
    /** Suffix that keeps variant codes unique across tests sharing the database. */
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        asCustomer = user(new CustomUserDetails(newCustomer()));
        tag = "-" + SEQ.incrementAndGet();
        JsonNode category = data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Phones " + tag))))
                .andExpect(status().isCreated()));
        categoryId = category.get("id").asLong();
    }

    // ------------------------------------------------------------------ admin + public

    @Test
    void productWithVariantsShowsOptionsLabelsAndDerivedPriceAndStock() throws Exception {
        long id = create(iphone());

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.optionNames", contains("Model", "Storage", "Color")))
                .andExpect(jsonPath("$.data.variants", hasSize(3)))
                .andExpect(jsonPath("$.data.variants[0].options", contains("iPhone 14", "128GB", "Black")))
                .andExpect(jsonPath("$.data.variants[0].sku").value("IP14-128-LLA" + tag))
                .andExpect(jsonPath("$.data.variants[1].price").value(300.00))
                .andExpect(jsonPath("$.data.variants[1].inStock").value(true))
                .andExpect(jsonPath("$.data.variants[2].imageUrl").value("/uploads/products/gold.png"))
                .andExpect(jsonPath("$.data.variants[0].imageUrl").value(nullValue()))
                // the product row carries the "from" price and the total stock
                .andExpect(jsonPath("$.data.price").value(100.00))
                .andExpect(jsonPath("$.data.stockQuantity").value(10))
                .andExpect(jsonPath("$.data.inStock").value(true));

        JsonNode product = data(mockMvc.perform(get("/api/v1/products/" + id)));
        assertThat(product.get("variants").get(1).get("label").asString())
                .isEqualTo("iPhone 14 Pro" + DOT + "256GB" + DOT + "Purple");

        // listings carry the variants too (batch-fetched) and the price filter uses the min
        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("maxPrice", "150"))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].variants", hasSize(3)));
    }

    @Test
    void variantsAreValidated() throws Exception {
        // a variant with fewer values than there are options
        Map<String, Object> mismatch = iphone();
        variantsOf(mismatch).set(0, variant(null, List.of("iPhone 14", "128GB"), "X1" + tag, 100, 1));
        expectFieldError(mismatch, "variants", "needs one value for each option");

        // two variants with the same combination (ignoring case and spaces)
        Map<String, Object> sameCombo = iphone();
        variantsOf(sameCombo).set(1,
                variant(null, List.of(" IPHONE 14", "128gb", "black "), "X2" + tag, 100, 1));
        expectFieldError(sameCombo, "variants", "same options");

        // the same code twice in one request
        Map<String, Object> sameSku = iphone();
        variantsOf(sameSku).set(1,
                variant(null, List.of("iPhone 14 Pro", "256GB", "Purple"), "ip14-128-lla" + tag, 1, 1));
        expectFieldError(sameSku, "variants", "is already used");

        // more than 3 options
        Map<String, Object> fourOptions = iphone();
        fourOptions.put("optionNames", List.of("Model", "Storage", "Color", "Carrier"));
        expectFieldError(fourOptions, "optionNames", "at most 3");

        // option names must differ
        Map<String, Object> dupNames = iphone();
        dupNames.put("optionNames", List.of("Model", "storage ", "Storage"));
        expectFieldError(dupNames, "optionNames", "different");

        // variants without option names
        Map<String, Object> noNames = iphone();
        noNames.remove("optionNames");
        expectFieldError(noNames, "optionNames", "before adding variants");

        // a code already used by another product's variant -> conflict
        create(iphone());
        Map<String, Object> other = iphone();
        other.put("sku", "OTHER" + tag);
        variantsOf(other).set(0,
                variant(null, List.of("iPhone 14", "128GB", "Black"), "IP14-128-LLA" + tag, 100, 1));
        variantsOf(other).set(1,
                variant(null, List.of("iPhone 14 Pro", "256GB", "Purple"), "NEW-1" + tag, 300, 1));
        variantsOf(other).set(2,
                variant(null, List.of("iPhone 14 Pro Max", "512GB", "Gold"), "NEW-2" + tag, 500, 1));
        mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Variant code IP14-128-LLA" + tag + " is already used"));
    }

    @Test
    void updateKeepsIdsAddsNewAndRemovesOrDisablesDroppedVariants() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long v14 = variants.get(0).get("id").asLong();
        long vPro = variants.get(1).get("id").asLong();
        long vMax = variants.get(2).get("id").asLong();

        // vPro gets ordered, so it can never be deleted
        addToCart(id, vPro, 1);
        checkout(createAddress(), "CASH_ON_DELIVERY");

        Map<String, Object> body = iphone();
        body.put("variants", new ArrayList<>(List.of(
                variant(v14, List.of("iPhone 14", "128GB", "Black"), "IP14-128-LLA" + tag, 90, 5),
                variant(null, List.of("iPhone 14", "256GB", "Blue"), "IP14-256-LLA" + tag, 150, 4))));
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants", hasSize(3)))
                .andExpect(jsonPath("$.data.variants[0].id").value(v14))
                .andExpect(jsonPath("$.data.variants[0].price").value(90.00))
                .andExpect(jsonPath("$.data.variants[1].sku").value("IP14-256-LLA" + tag))
                // the ordered one is kept for history but switched off, at the end
                .andExpect(jsonPath("$.data.variants[2].id").value(vPro))
                .andExpect(jsonPath("$.data.variants[2].enabled").value(false))
                .andExpect(jsonPath("$.data.price").value(90.00))
                .andExpect(jsonPath("$.data.stockQuantity").value(9));

        // never ordered -> really gone
        assertThat(variantRepository.existsById(vMax)).isFalse();
        assertThat(variantRepository.existsById(vPro)).isTrue();

        // shoppers only see the enabled ones
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.variants", hasSize(2)))
                .andExpect(jsonPath("$.data.variants[1].options", contains("iPhone 14", "256GB", "Blue")));

        // variants omitted = unchanged; [] = remove them all (the ordered one stays disabled)
        Map<String, Object> untouched = iphone();
        untouched.remove("variants");
        untouched.remove("optionNames");
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(untouched)))
                .andExpect(jsonPath("$.data.variants", hasSize(3)))
                .andExpect(jsonPath("$.data.optionNames", hasSize(3)));

        Map<String, Object> none = iphone();
        none.put("variants", List.of());
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(none)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants", hasSize(1)))
                .andExpect(jsonPath("$.data.variants[0].enabled").value(false));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.variants", empty()));
    }

    @Test
    void disabledVariantIsHiddenPubliclyAndCannotBeBought() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long v14 = variants.get(0).get("id").asLong();

        // someone already has the 14 in their cart
        addToCart(id, v14, 1);

        Map<String, Object> body = iphone();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) variantsOf(body).get(0);
        first.put("id", v14);
        first.put("enabled", false);
        for (int i = 1; i < 3; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> v = (Map<String, Object>) variantsOf(body).get(i);
            v.put("id", variants.get(i).get("id").asLong());
        }
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].enabled").value(false))
                .andExpect(jsonPath("$.data.variants[0].inStock").value(false))
                // the cheapest enabled one is now the Pro
                .andExpect(jsonPath("$.data.price").value(300.00))
                .andExpect(jsonPath("$.data.stockQuantity").value(5));

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.variants", hasSize(2)))
                .andExpect(jsonPath("$.data.variants[0].options[0]").value("iPhone 14 Pro"));

        // the stale cart line was dropped
        mockMvc.perform(get("/api/v1/cart").with(asCustomer))
                .andExpect(jsonPath("$.data.itemCount").value(0));

        mockMvc.perform(cartAdd(id, v14, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no longer available")));
    }

    // ------------------------------------------------------------------ cart

    @Test
    void cartLinesArePerVariant() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long v14 = variants.get(0).get("id").asLong();
        long vPro = variants.get(1).get("id").asLong();

        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":1}".formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please choose an option"));

        mockMvc.perform(cartAdd(id, vPro, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].variantId").value(vPro))
                .andExpect(jsonPath("$.data.items[0].sku").value("IP14P-256-LLA" + tag))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(300.00))
                .andExpect(jsonPath("$.data.items[0].availableStock").value(3))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("/uploads/products/main.png"));

        JsonNode cart = data(mockMvc.perform(get("/api/v1/cart").with(asCustomer)));
        assertThat(cart.get("items").get(0).get("variantLabel").asString())
                .isEqualTo("iPhone 14 Pro" + DOT + "256GB" + DOT + "Purple");

        // same variant again -> same line, more units
        mockMvc.perform(cartAdd(id, vPro, 1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(600.00));

        // a different variant of the same product -> its own line
        mockMvc.perform(cartAdd(id, v14, 1))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.subtotal").value(700.00))
                .andExpect(jsonPath("$.data.checkoutReady").value(true));

        // stock is the variant's (3 Pros), not the product total (10)
        mockMvc.perform(cartAdd(id, vPro, 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("left in stock")));

        // a variant id from some other product is rejected
        long otherId = create(iphoneWithCodes("B"));
        long foreign = adminProduct(otherId).get("variants").get(0).get("id").asLong();
        mockMvc.perform(cartAdd(id, foreign, 1))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void checkoutSnapshotsTheVariantAndCancellationRestoresStock() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long v14 = variants.get(0).get("id").asLong();
        long vPro = variants.get(1).get("id").asLong();
        long addressId = createAddress();

        addToCart(id, vPro, 2);
        addToCart(id, v14, 1);

        JsonNode order = data(mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"
                                .formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(700.00))
                .andExpect(jsonPath("$.data.totalAmount").value(701.50))
                .andExpect(jsonPath("$.data.items", hasSize(2))));

        JsonNode proLine = lineFor(order, vPro);
        assertThat(proLine.get("variantLabel").asString())
                .isEqualTo("iPhone 14 Pro" + DOT + "256GB" + DOT + "Purple");
        assertThat(proLine.get("sku").asString()).isEqualTo("IP14P-256-LLA" + tag);
        assertThat(proLine.get("unitPrice").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(proLine.get("quantity").asInt()).isEqualTo(2);
        assertThat(proLine.get("productId").asLong()).isEqualTo(id);

        // variant stock reserved, product total follows
        assertThat(variantStock(vPro)).isEqualTo(1);
        assertThat(variantStock(v14)).isEqualTo(4);
        assertThat(productStock(id)).isEqualTo(7);

        long orderId = order.get("id").asLong();
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.items[0].variantId").isNumber());

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(variantStock(vPro)).isEqualTo(3);
        assertThat(variantStock(v14)).isEqualTo(5);
        assertThat(productStock(id)).isEqualTo(10);
    }

    @Test
    void checkoutRefusesWhenTheVariantRanOut() throws Exception {
        long id = create(iphone());
        long vMax = adminProduct(id).get("variants").get(2).get("id").asLong();
        long addressId = createAddress();
        addToCart(id, vMax, 2);

        // someone else bought one meanwhile
        var variant = variantRepository.findById(vMax).orElseThrow();
        variant.setStockQuantity(1);
        variantRepository.save(variant);

        mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"KHQR\"}".formatted(addressId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Not enough stock")));
        assertThat(variantStock(vMax)).isEqualTo(1);
    }

    @Test
    void removingAllVariantsOfAnOrderedProductMakesItPlainAgain() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long vPro = variants.get(1).get("id").asLong();
        long addressId = createAddress();

        // vPro is ordered, so it can only be retired, never deleted
        addToCart(id, vPro, 2);
        long orderId = checkout(addressId, "CASH_ON_DELIVERY");
        assertThat(variantStock(vPro)).isEqualTo(1);

        // the admin turns it back into a plain product with its own price and stock
        Map<String, Object> plain = iphone();
        plain.put("optionNames", List.of());
        plain.put("variants", List.of());
        plain.put("price", 49.99);
        plain.put("stockQuantity", 7);
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(plain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(49.99))
                .andExpect(jsonPath("$.data.stockQuantity").value(7))
                .andExpect(jsonPath("$.data.optionNames", empty()))
                // the retired row is order history only - not offered to the editor
                .andExpect(jsonPath("$.data.variants", empty()));
        assertThat(variantRepository.existsById(vPro)).isTrue();

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(jsonPath("$.data.price").value(49.99))
                .andExpect(jsonPath("$.data.stockQuantity").value(7))
                .andExpect(jsonPath("$.data.inStock").value(true))
                .andExpect(jsonPath("$.data.variants", empty()));

        // cancelling the old variant order must not touch the plain product's stock
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk());
        assertThat(variantStock(vPro)).isEqualTo(3);
        assertThat(productStock(id)).isEqualTo(7);

        // and it sells as a plain product now
        addToCart(id, null, 2);
        long plainOrder = checkout(addressId, "CASH_ON_DELIVERY");
        assertThat(productStock(id)).isEqualTo(5);
        mockMvc.perform(get("/api/v1/orders/" + plainOrder).with(asCustomer))
                .andExpect(jsonPath("$.data.items[0].variantId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(49.99));
        mockMvc.perform(patch("/api/v1/orders/" + plainOrder + "/cancel").with(asCustomer))
                .andExpect(status().isOk());
        assertThat(productStock(id)).isEqualTo(7);
    }

    @Test
    void retiringEveryVariantButKeepingOptionsUsesTheRequestPriceAndStock() throws Exception {
        long id = create(iphone());
        long vPro = adminProduct(id).get("variants").get(1).get("id").asLong();
        addToCart(id, vPro, 1);
        checkout(createAddress(), "CASH_ON_DELIVERY");

        Map<String, Object> none = iphone();
        none.put("variants", List.of());
        none.put("price", 20);
        none.put("stockQuantity", 4);
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(none)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(20.00))
                .andExpect(jsonPath("$.data.stockQuantity").value(4));
        assertThat(productStock(id)).isEqualTo(4);
    }

    @Test
    void checkoutOfTwoVariantsKeepsProductStockEqualToTheSumOfVariants() throws Exception {
        long id = create(iphone());
        JsonNode variants = adminProduct(id).get("variants");
        long v14 = variants.get(0).get("id").asLong();
        long vPro = variants.get(1).get("id").asLong();
        long vMax = variants.get(2).get("id").asLong();

        addToCart(id, vMax, 1);
        addToCart(id, v14, 3);
        addToCart(id, vPro, 2);
        long orderId = checkout(createAddress(), "CASH_ON_DELIVERY");

        assertThat(variantStock(v14)).isEqualTo(2);
        assertThat(variantStock(vPro)).isEqualTo(1);
        assertThat(variantStock(vMax)).isEqualTo(1);
        assertThat(productStock(id))
                .isEqualTo(variantStock(v14) + variantStock(vPro) + variantStock(vMax))
                .isEqualTo(4);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        assertThat(productStock(id))
                .isEqualTo(variantStock(v14) + variantStock(vPro) + variantStock(vMax))
                .isEqualTo(10);
    }

    @Test
    void cancellingAPlainOrderAfterVariantsWereAddedKeepsStockEqualToTheSumOfVariants() throws Exception {
        // sold while plain
        Map<String, Object> plain = iphone();
        plain.put("optionNames", List.of());
        plain.put("variants", List.of());
        plain.put("price", 49.99);
        plain.put("stockQuantity", 6);
        long id = create(plain);
        addToCart(id, null, 2);
        long orderId = checkout(createAddress(), "CASH_ON_DELIVERY");
        assertThat(productStock(id)).isEqualTo(4);

        // the admin then adds options; the stock now comes from the variants
        mockMvc.perform(put("/api/v1/admin/products/" + id).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(iphone())))
                .andExpect(status().isOk());
        JsonNode variants = adminProduct(id).get("variants");
        int sum = 0;
        for (JsonNode v : variants) {
            sum += variantStock(v.get("id").asLong());
        }
        assertThat(productStock(id)).isEqualTo(sum);

        // cancelling the plain line must not push the product above its variants' total
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(productStock(id)).isEqualTo(sum).isEqualTo(10);
    }

    // ------------------------------------------------------------------ plain products

    @Test
    void productWithoutVariantsWorksExactlyAsBefore() throws Exception {
        String sku = "PLAIN" + tag;
        long id = data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"Case","sku":"%s","price":12.50,"stockQuantity":4}
                                """.formatted(categoryId, sku)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.optionNames", empty()))
                .andExpect(jsonPath("$.data.variants", empty()))
                .andExpect(jsonPath("$.data.price").value(12.50))).get("id").asLong();

        mockMvc.perform(post("/api/v1/cart/items").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":3}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].variantId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].variantLabel").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].sku").value(sku))
                .andExpect(jsonPath("$.data.subtotal").value(37.50));

        // adding again merges, as before
        addToCart(id, null, 1);
        mockMvc.perform(get("/api/v1/cart").with(asCustomer))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].quantity").value(4));

        long orderId = checkout(createAddress(), "CASH_ON_DELIVERY");
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(asCustomer))
                .andExpect(jsonPath("$.data.items[0].variantId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].variantLabel").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].sku").value(sku))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(12.50));
        assertThat(productStock(id)).isZero();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(asCustomer))
                .andExpect(status().isOk());
        assertThat(productStock(id)).isEqualTo(4);
    }

    // ------------------------------------------------------------------ helpers

    /** The shop owner's example: one "iPhone 14" with three option combinations. */
    private Map<String, Object> iphone() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", "iPhone 14");
        body.put("sku", "IP14" + tag + "-" + SEQ.incrementAndGet());
        // ignored when variants exist; the frontend sends the computed min / sum
        body.put("price", 1);
        body.put("stockQuantity", 0);
        body.put("imageUrl", "/uploads/products/main.png");
        body.put("optionNames", List.of("Model", "Storage", "Color"));
        List<Object> variants = new ArrayList<>();
        variants.add(variant(null, List.of("iPhone 14", "128GB", "Black"), "IP14-128-LLA" + tag, 100, 5));
        variants.add(variant(null, List.of("iPhone 14 Pro", "256GB", "Purple"), "IP14P-256-LLA" + tag, 300, 3));
        Map<String, Object> max = variant(null, List.of("iPhone 14 Pro Max", "512GB", "Gold"),
                "IP14PM-512-LLA" + tag, 500, 2);
        max.put("imageUrl", "/uploads/products/gold.png");
        variants.add(max);
        body.put("variants", variants);
        return body;
    }

    private Map<String, Object> iphoneWithCodes(String prefix) {
        Map<String, Object> body = iphone();
        for (Object v : variantsOf(body)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variant = (Map<String, Object>) v;
            variant.put("sku", prefix + variant.get("sku"));
        }
        return body;
    }

    private static Map<String, Object> variant(Long id, List<String> options, String sku,
                                               double price, int stock) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (id != null) {
            v.put("id", id);
        }
        v.put("options", options);
        v.put("sku", sku);
        v.put("price", price);
        v.put("stockQuantity", stock);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> variantsOf(Map<String, Object> body) {
        return (List<Object>) body.get("variants");
    }

    private void expectFieldError(Map<String, Object> body, String field, String fragment) throws Exception {
        mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).value(containsString(fragment)));
    }

    private long create(Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode adminProduct(long id) throws Exception {
        return data(mockMvc.perform(get("/api/v1/admin/products/" + id).with(asAdmin))
                .andExpect(status().isOk()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cartAdd(
            long productId, Long variantId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        if (variantId != null) {
            body.put("variantId", variantId);
        }
        return post("/api/v1/cart/items").with(asCustomer)
                .contentType(MediaType.APPLICATION_JSON).content(json(body));
    }

    private void addToCart(long productId, Long variantId, int quantity) throws Exception {
        mockMvc.perform(cartAdd(productId, variantId, quantity)).andExpect(status().isOk());
    }

    private long checkout(long addressId, String method) throws Exception {
        return data(mockMvc.perform(post("/api/v1/orders/checkout").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"%s\"}".formatted(addressId, method)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createAddress() throws Exception {
        return data(mockMvc.perform(post("/api/v1/addresses").with(asCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"Sok Dara","phone":"012345678",
                                 "province":"Phnom Penh","district":"Chamkarmon",
                                 "addressDetail":"No. 12, St. 271"}
                                """))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static JsonNode lineFor(JsonNode order, long variantId) {
        for (JsonNode item : order.get("items")) {
            if (item.get("variantId").asLong() == variantId) {
                return item;
            }
        }
        throw new AssertionError("No order line for variant " + variantId);
    }

    private JsonNode data(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data");
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private int variantStock(long variantId) {
        return variantRepository.findById(variantId).orElseThrow().getStockQuantity();
    }

    private int productStock(long productId) {
        return productRepository.findById(productId).orElseThrow().getStockQuantity();
    }

    private User newCustomer() {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName("Variant Shopper " + n)
                .email("variant" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        return userRepository.save(user);
    }
}
