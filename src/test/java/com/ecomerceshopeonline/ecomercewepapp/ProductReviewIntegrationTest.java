package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.DataSeeder;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.mapper.ReviewMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.ProductRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import com.ecomerceshopeonline.ecomercewepapp.security.CustomUserDetails;
import com.ecomerceshopeonline.ecomercewepapp.service.BakongClient;
import com.ecomerceshopeonline.ecomercewepapp.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Star ratings and reviews: who may write one (only after delivery), editing and deleting
 * one's own, the product's derived rating (recomputed on every change, hidden reviews left
 * out), admin moderation, and the catalogue's rating fields and "Top rated" sort.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviews_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.bakong.account-id=test@devb",
        "app.bakong.merchant-name=Test Merchant",
        "app.bakong.merchant-city=Phnom Penh",
        "app.bakong.token=test-token",
        "app.bakong.expiry-minutes=30"
})
class ProductReviewIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String NOT_ELIGIBLE = "Only customers who received this product can review it";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;

    @MockitoBean private EmailService emailService;
    @MockitoBean private BakongClient bakongClient;

    private MockMvc mockMvc;
    private final RequestPostProcessor asAdmin = user("admin").roles("ADMIN");
    private long categoryId;

    /** A signed-in customer with a saved delivery address. */
    private record Shopper(User user, RequestPostProcessor auth, long addressId) {
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dataSeeder.run(null);
        categoryId = createCategory("Reviews");
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void onlyACustomerWhoseOrderWasDeliveredMayReview() throws Exception {
        long productId = createProduct("Kettle");
        Shopper shopper = newShopper("Dara Sok");

        // guests are asked to sign in
        mockMvc.perform(get(reviewsOf(productId) + "/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(reviewsOf(productId)).contentType(MediaType.APPLICATION_JSON)
                .content(review(5, null, null))).andExpect(status().isUnauthorized());

        // never ordered
        me(shopper, productId)
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_PURCHASED"))
                .andExpect(jsonPath("$.data.review").value(nullValue()));
        create(shopper, productId, review(5, null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(NOT_ELIGIBLE));

        // ordered, on its way
        long orderId = placeOrder(shopper, productId);
        me(shopper, productId)
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_DELIVERED"));
        create(shopper, productId, review(5, null, null)).andExpect(status().isForbidden());
        setStatus(orderId, "SHIPPED");
        me(shopper, productId).andExpect(jsonPath("$.data.reason").value("NOT_DELIVERED"));
        create(shopper, productId, review(5, null, null)).andExpect(status().isForbidden());

        // delivered
        setStatus(orderId, "DELIVERED");
        me(shopper, productId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andExpect(jsonPath("$.data.reason").value(nullValue()))
                .andExpect(jsonPath("$.data.review").value(nullValue()));
        create(shopper, productId, review(5, null, null)).andExpect(status().isCreated());
    }

    @Test
    void aCancelledOrderDoesNotMakeAPurchase() throws Exception {
        long productId = createProduct("Toaster");
        Shopper shopper = newShopper("Vanna Lim");
        long orderId = placeOrder(shopper, productId);
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel").with(shopper.auth()))
                .andExpect(status().isOk());

        me(shopper, productId)
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_PURCHASED"));
        create(shopper, productId, review(4, null, null)).andExpect(status().isForbidden());
    }

    @Test
    void administratorsCannotWriteReviews() throws Exception {
        long productId = createProduct("Blender");
        RequestPostProcessor admin = user(new CustomUserDetails(userRepository.findByEmail(adminEmail).orElseThrow()));

        mockMvc.perform(get(reviewsOf(productId) + "/me").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_PURCHASED"));
        mockMvc.perform(post(reviewsOf(productId)).with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(review(5, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(NOT_ELIGIBLE));
    }

    // ------------------------------------------------------------------ own review

    @Test
    void aShopperCreatesEditsAndDeletesTheirReview() throws Exception {
        JsonNode product = createVariantProduct("Phone case");
        long productId = product.get("id").asLong();
        long blackId = product.get("variants").get(0).get("id").asLong();
        Shopper shopper = newShopper("Dara Sok");
        setStatus(placeVariantOrder(shopper, productId, blackId), "DELIVERED");

        JsonNode created = data(create(shopper, productId, review(4, "  Fits well ", "  Snug, good grip.  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.title").value("Fits well"))
                .andExpect(jsonPath("$.data.comment").value("Snug, good grip."))
                .andExpect(jsonPath("$.data.variantLabel").value("Black"))
                .andExpect(jsonPath("$.data.authorName").value("Dara S."))
                .andExpect(jsonPath("$.data.authorAvatar").value(nullValue()))
                .andExpect(jsonPath("$.data.verifiedPurchase").value(true))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty()));
        long reviewId = created.get("id").asLong();

        // the review never exposes the account's email or full name
        assertThat(created.toString()).doesNotContain(shopper.user().getEmail()).doesNotContain("Dara Sok");

        me(shopper, productId)
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.reason").value("ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.data.review.id").value(reviewId))
                .andExpect(jsonPath("$.data.review.mine").value(true));

        // one review per product - the client edits instead
        create(shopper, productId, review(5, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already reviewed this product"));

        assertRating(productId, 4.0, 1);

        mockMvc.perform(put("/api/v1/reviews/" + reviewId).with(shopper.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(review(5, "   ", "Even better after a month")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reviewId))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.title").value(nullValue()))
                .andExpect(jsonPath("$.data.comment").value("Even better after a month"))
                .andExpect(jsonPath("$.data.variantLabel").value("Black"));
        assertRating(productId, 5.0, 1);

        mockMvc.perform(delete("/api/v1/reviews/" + reviewId).with(shopper.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Review deleted"));
        assertRating(productId, 0.0, 0);
        me(shopper, productId)
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andExpect(jsonPath("$.data.review").value(nullValue()));
        mockMvc.perform(put("/api/v1/reviews/" + reviewId).with(shopper.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(review(3, null, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewFieldsAreValidated() throws Exception {
        long productId = createProduct("Rice cooker");
        Shopper shopper = newShopper("Bopha");
        setStatus(placeOrder(shopper, productId), "DELIVERED");

        create(shopper, productId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").value("Choose a star rating"));
        create(shopper, productId, review(6, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").value("Rating must be between 1 and 5"));
        create(shopper, productId, review(0, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
        create(shopper, productId, review(3, "t".repeat(101), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        create(shopper, productId, review(3, null, "c".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());

        // limits apply to the trimmed text; a single name stays as it is
        create(shopper, productId, review(3, null, "   " + "c".repeat(1000) + "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comment").value("c".repeat(1000)))
                .andExpect(jsonPath("$.data.authorName").value("Bopha"));
    }

    @Test
    void onlyTheAuthorCanChangeOrDeleteAReview() throws Exception {
        long productId = createProduct("Lamp");
        Shopper author = newShopper("Sreyneang Chea");
        Shopper other = newShopper("Rithy Mao");
        setStatus(placeOrder(author, productId), "DELIVERED");
        setStatus(placeOrder(other, productId), "DELIVERED");
        long reviewId = data(create(author, productId, review(5, "Bright", null))
                .andExpect(status().isCreated())).get("id").asLong();
        RequestPostProcessor admin = user(new CustomUserDetails(userRepository.findByEmail(adminEmail).orElseThrow()));

        mockMvc.perform(put("/api/v1/reviews/" + reviewId).with(other.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(review(1, "Mine now", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/reviews/" + reviewId).with(other.auth()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/reviews/" + reviewId).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(review(1, null, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/reviews/" + reviewId)
                        .contentType(MediaType.APPLICATION_JSON).content(review(1, null, null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/reviews/987654321").with(author.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(review(1, null, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/reviews/987654321").with(author.auth()))
                .andExpect(status().isNotFound());

        // untouched
        assertRating(productId, 5.0, 1);
        // "mine" marks the viewer's own review in the public list
        mockMvc.perform(get(reviewsOf(productId)).with(author.auth()))
                .andExpect(jsonPath("$.data.content[0].mine").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Bright"));
        mockMvc.perform(get(reviewsOf(productId)).with(other.auth()))
                .andExpect(jsonPath("$.data.content[0].mine").value(false));
        mockMvc.perform(get(reviewsOf(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mine").value(false))
                .andExpect(jsonPath("$.data.content[0].authorName").value("Sreyneang C."));
    }

    // ------------------------------------------------------------------ rating

    @Test
    void theRatingFollowsTheVisibleReviews() throws Exception {
        long productId = createProduct("Fan");
        Shopper five = newShopper("Sokha Chan");
        Shopper four = newShopper("Dara Sok");
        Shopper two = newShopper("Kanha Ny");
        for (Shopper shopper : List.of(five, four, two)) {
            setStatus(placeOrder(shopper, productId), "DELIVERED");
        }
        long fiveId = data(create(five, productId, review(5, "Great", null))).get("id").asLong();
        create(four, productId, review(4, null, "Good")).andExpect(status().isCreated());
        long twoId = data(create(two, productId, review(2, "Noisy", null))).get("id").asLong();

        mockMvc.perform(get(reviewsOf(productId) + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.average").value(3.7))
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.distribution['5']").value(1))
                .andExpect(jsonPath("$.data.distribution['4']").value(1))
                .andExpect(jsonPath("$.data.distribution['3']").value(0))
                .andExpect(jsonPath("$.data.distribution['2']").value(1))
                .andExpect(jsonPath("$.data.distribution['1']").value(0));
        assertRating(productId, 3.7, 3);

        // sorting, filtering and paging the public list
        mockMvc.perform(get(reviewsOf(productId)).param("sort", "highest"))
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[2].rating").value(2));
        mockMvc.perform(get(reviewsOf(productId)).param("sort", "lowest"))
                .andExpect(jsonPath("$.data.content[0].rating").value(2));
        mockMvc.perform(get(reviewsOf(productId)))
                .andExpect(jsonPath("$.data.content[0].id").value(twoId));  // newest first
        mockMvc.perform(get(reviewsOf(productId)).param("rating", "4"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].comment").value("Good"));
        mockMvc.perform(get(reviewsOf(productId)).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(true));
        mockMvc.perform(get(reviewsOf(productId)).param("rating", "9"))
                .andExpect(status().isBadRequest());

        // hiding takes it out of the list and the rating - without counting as an edit
        String updatedBefore = adminRow(twoId).get("updatedAt").asText();
        mockMvc.perform(patch("/api/v1/admin/reviews/" + twoId + "/visibility").with(asAdmin)
                        .param("visible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.message").value("Review hidden"));
        assertRating(productId, 4.5, 2);
        mockMvc.perform(get(reviewsOf(productId) + "/summary"))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.distribution['2']").value(0));
        mockMvc.perform(get(reviewsOf(productId)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[?(@.id == " + twoId + ")]").isEmpty());
        mockMvc.perform(get(reviewsOf(productId)).param("rating", "2"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        // its author still sees it (and that it is hidden)
        me(two, productId)
                .andExpect(jsonPath("$.data.reason").value("ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.data.review.visible").value(false));
        assertThat(adminRow(twoId).get("updatedAt").asText()).isEqualTo(updatedBefore);

        mockMvc.perform(patch("/api/v1/admin/reviews/" + twoId + "/visibility").with(asAdmin)
                        .param("visible", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true));
        assertRating(productId, 3.7, 3);

        // an admin deleting one recomputes too: (4 + 2) / 2
        mockMvc.perform(delete("/api/v1/admin/reviews/" + fiveId).with(asAdmin))
                .andExpect(status().isOk());
        assertRating(productId, 3.0, 2);
        mockMvc.perform(get(reviewsOf(productId) + "/summary"))
                .andExpect(jsonPath("$.data.distribution['5']").value(0));
        // and its author may review again
        me(five, productId).andExpect(jsonPath("$.data.canReview").value(true));
    }

    @Test
    void reviewsOfMissingOrHiddenProductsAreNotFound() throws Exception {
        mockMvc.perform(get(reviewsOf(987654321L))).andExpect(status().isNotFound());
        mockMvc.perform(get(reviewsOf(987654321L) + "/summary")).andExpect(status().isNotFound());

        long productId = createProduct("Retired");
        mockMvc.perform(get(reviewsOf(productId) + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.average").value(0.0))
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.distribution['1']").value(0));
        mockMvc.perform(get(reviewsOf(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(patch("/api/v1/admin/products/" + productId + "/status").with(asAdmin)
                .param("enabled", "false")).andExpect(status().isOk());
        mockMvc.perform(get(reviewsOf(productId))).andExpect(status().isNotFound());
        mockMvc.perform(get(reviewsOf(productId) + "/summary")).andExpect(status().isNotFound());
    }

    @Test
    void authorNamesAreFirstNameAndLastInitial() throws Exception {
        assertThat(ReviewMapper.publicName("Dara Sok")).isEqualTo("Dara S.");
        assertThat(ReviewMapper.publicName("  sok   dara  chan ")).isEqualTo("sok C.");
        assertThat(ReviewMapper.publicName("Bopha")).isEqualTo("Bopha");
        assertThat(ReviewMapper.publicName("ដារា សុខ")).isEqualTo("ដារា ស.");
        assertThat(ReviewMapper.publicName("sokha.chan@gmail.com")).isEqualTo("S.");
        assertThat(ReviewMapper.publicName("   ")).isEqualTo("Customer");
        assertThat(ReviewMapper.publicName(null)).isEqualTo("Customer");

        // a Google account without a name carries its email as the name - never shown
        long productId = createProduct("Mug");
        Shopper shopper = newShopper("sokha.chan" + SEQ.incrementAndGet() + "@gmail.com");
        shopper.user().setProfileImage("https://cdn.test/avatar.png");
        userRepository.save(shopper.user());
        setStatus(placeOrder(shopper, productId), "DELIVERED");
        create(shopper, productId, review(5, null, null)).andExpect(status().isCreated());

        String body = mockMvc.perform(get(reviewsOf(productId)))
                .andExpect(jsonPath("$.data.content[0].authorName").value("S."))
                .andExpect(jsonPath("$.data.content[0].authorAvatar").value("https://cdn.test/avatar.png"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("@gmail.com").doesNotContain("@test.local");
    }

    // ------------------------------------------------------------------ admin

    @Test
    void adminsModerateReviews() throws Exception {
        String productName = "Moderated lamp " + SEQ.incrementAndGet();
        long productId = createProduct(productName);
        Shopper shopper = newShopper("Sophea Keo");
        setStatus(placeOrder(shopper, productId), "DELIVERED");
        long reviewId = data(create(shopper, productId, review(3, "Okay", "Dim light"))).get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin).param("search", productName.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].productId").value(productId))
                .andExpect(jsonPath("$.data.content[0].productName").value(productName))
                .andExpect(jsonPath("$.data.content[0].productImageUrl").value("/uploads/products/lamp.png"))
                .andExpect(jsonPath("$.data.content[0].rating").value(3))
                .andExpect(jsonPath("$.data.content[0].title").value("Okay"))
                .andExpect(jsonPath("$.data.content[0].comment").value("Dim light"))
                .andExpect(jsonPath("$.data.content[0].authorId").value(shopper.user().getId()))
                .andExpect(jsonPath("$.data.content[0].authorName").value("Sophea Keo"))
                .andExpect(jsonPath("$.data.content[0].authorEmail").value(shopper.user().getEmail()))
                .andExpect(jsonPath("$.data.content[0].visible").value(true));
        // by the author's email or name
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin).param("search", shopper.user().getEmail()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin)
                        .param("search", productName).param("rating", "1"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin)
                        .param("search", productName).param("visible", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/visibility").with(asAdmin)
                .param("visible", "false")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin)
                        .param("search", productName).param("visible", "false").param("rating", "3"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        // every review, newest first, across products
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(reviewId));

        // customers and guests stay out
        mockMvc.perform(get("/api/v1/admin/reviews").with(shopper.auth())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId).with(shopper.auth()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Review deleted"));
        mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId).with(asAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/visibility").with(asAdmin)
                .param("visible", "true")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin).param("search", productName))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertRating(productId, 0.0, 0);

        mockMvc.perform(get("/api/v1/admin/dashboard").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.averageRating").isNumber())
                .andExpect(jsonPath("$.data.reviews.total").isNumber())
                .andExpect(jsonPath("$.data.reviews.hidden").isNumber())
                .andExpect(jsonPath("$.data.reviews.newInPeriod").isNumber());
    }

    // ------------------------------------------------------------------ catalogue

    @Test
    void theCatalogueShowsRatingsAndSortsByThem() throws Exception {
        long good = createProduct("Good one");
        long best = createProduct("Best one");
        long unrated = createProduct("Unrated one");
        long single = createProduct("Single five");
        Shopper first = newShopper("Chanthy Ouk");
        Shopper second = newShopper("Piseth Ros");
        setStatus(placeOrder(first, good, best, single), "DELIVERED");
        setStatus(placeOrder(second, best), "DELIVERED");
        create(first, good, review(4, null, null)).andExpect(status().isCreated());
        create(first, best, review(5, null, null)).andExpect(status().isCreated());
        create(second, best, review(5, null, null)).andExpect(status().isCreated());
        create(first, single, review(5, null, null)).andExpect(status().isCreated());

        // "Top rated": best average first, then the most reviews; unrated last
        for (String sort : List.of("rating", "rating,desc")) {
            mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(categoryId))
                            .param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(best))
                    .andExpect(jsonPath("$.data.content[0].rating").value(5.0))
                    .andExpect(jsonPath("$.data.content[0].reviewCount").value(2))
                    .andExpect(jsonPath("$.data.content[1].id").value(single))
                    .andExpect(jsonPath("$.data.content[2].id").value(good))
                    .andExpect(jsonPath("$.data.content[2].rating").value(4.0))
                    .andExpect(jsonPath("$.data.content[3].id").value(unrated))
                    .andExpect(jsonPath("$.data.content[3].rating").value(0.0))
                    .andExpect(jsonPath("$.data.content[3].reviewCount").value(0));
        }
        mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(categoryId))
                        .param("sort", "reviewCount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(best));

        // the product page and the admin views carry it too
        mockMvc.perform(get("/api/v1/products/" + good))
                .andExpect(jsonPath("$.data.rating").value(4.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1));
        mockMvc.perform(get("/api/v1/admin/products/" + best).with(asAdmin))
                .andExpect(jsonPath("$.data.rating").value(5.0))
                .andExpect(jsonPath("$.data.reviewCount").value(2));
        mockMvc.perform(get("/api/v1/admin/products").with(asAdmin).param("sort", "rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rating").value(5.0));

        // a review does not count as editing the product
        assertThat(productRepository.findById(good).orElseThrow().getRatingCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ clean-up paths

    @Test
    void reviewsGoWithTheirAuthorOrProductAndTheRatingFollows() throws Exception {
        RequestPostProcessor admin = user(new CustomUserDetails(userRepository.findByEmail(adminEmail).orElseThrow()));
        long productId = createProduct("Kept product");

        // Settings > Data > Orders: reviews are about the product, they stay
        Shopper first = newShopper("Mony Heng");
        setStatus(placeOrder(first, productId), "DELIVERED");
        create(first, productId, review(5, null, null)).andExpect(status().isCreated());
        wipe(admin, List.of("ORDERS"));
        assertRating(productId, 5.0, 1);

        // ...so a customer without orders may still have reviews: deleting them takes those along
        mockMvc.perform(delete("/api/v1/admin/customers/" + first.user().getId()).with(asAdmin))
                .andExpect(status().isOk());
        assertRating(productId, 0.0, 0);

        // Settings > Data > Orders + Customers recomputes the ratings of the products that stay
        Shopper second = newShopper("Lina Touch");
        setStatus(placeOrder(second, productId), "DELIVERED");
        create(second, productId, review(4, null, null)).andExpect(status().isCreated());
        assertRating(productId, 4.0, 1);
        wipe(admin, List.of("ORDERS", "CUSTOMERS"));
        assertRating(productId, 0.0, 0);

        // a product whose orders were wiped can be deleted together with its reviews
        Shopper third = newShopper("Nita Prak");
        setStatus(placeOrder(third, productId), "DELIVERED");
        long reviewId = data(create(third, productId, review(3, null, null))).get("id").asLong();
        wipe(admin, List.of("ORDERS"));
        mockMvc.perform(delete("/api/v1/admin/products/" + productId).with(asAdmin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId).with(asAdmin))
                .andExpect(status().isNotFound());
    }

    private void wipe(RequestPostProcessor admin, List<String> groups) throws Exception {
        mockMvc.perform(post("/api/v1/admin/data/delete").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", adminPassword, "confirm", "DELETE", "groups", groups))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private static String reviewsOf(long productId) {
        return "/api/v1/products/" + productId + "/reviews";
    }

    private String review(int rating, String title, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", rating);
        if (title != null) {
            body.put("title", title);
        }
        if (comment != null) {
            body.put("comment", comment);
        }
        return objectMapper.writeValueAsString(body);
    }

    private ResultActions create(Shopper shopper, long productId, String body) throws Exception {
        return mockMvc.perform(post(reviewsOf(productId)).with(shopper.auth())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions me(Shopper shopper, long productId) throws Exception {
        return mockMvc.perform(get(reviewsOf(productId) + "/me").with(shopper.auth()))
                .andExpect(status().isOk());
    }

    private void assertRating(long productId, double average, int count) throws Exception {
        mockMvc.perform(get("/api/v1/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(average))
                .andExpect(jsonPath("$.data.reviewCount").value(count));
        assertThat(productRepository.findById(productId).orElseThrow().getRatingAverage())
                .isEqualByComparingTo(BigDecimal.valueOf(average));
    }

    private JsonNode adminRow(long reviewId) throws Exception {
        JsonNode content = data(mockMvc.perform(get("/api/v1/admin/reviews").with(asAdmin).param("size", "100"))
                .andExpect(status().isOk())).get("content");
        for (JsonNode row : content) {
            if (row.get("id").asLong() == reviewId) {
                return row;
            }
        }
        throw new AssertionError("Review " + reviewId + " not in the admin list");
    }

    private Shopper newShopper(String fullName) throws Exception {
        int n = SEQ.incrementAndGet();
        User user = User.builder()
                .fullName(fullName)
                .email("review-shopper" + n + "@test.local")
                .password(passwordEncoder.encode("Secret123"))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        user.addRole(roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow());
        user = userRepository.save(user);
        RequestPostProcessor auth = user(new CustomUserDetails(user));
        long addressId = data(mockMvc.perform(post("/api/v1/addresses").with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"Sok Dara","phone":"012345678",
                                 "province":"Phnom Penh","district":"Chamkarmon",
                                 "addressDetail":"No. 12, St. 271"}
                                """))
                .andExpect(status().isCreated())).get("id").asLong();
        return new Shopper(user, auth, addressId);
    }

    private long createCategory(String name) throws Exception {
        return data(mockMvc.perform(post("/api/v1/admin/categories").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s-%d\"}".formatted(name, SEQ.incrementAndGet())))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createProduct(String name) throws Exception {
        return data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"%s","sku":"REV-%d","price":10.00,"stockQuantity":100,
                                 "imageUrl":"/uploads/products/lamp.png"}
                                """.formatted(categoryId, name, SEQ.incrementAndGet())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.rating").value(0))).get("id").asLong();
    }

    private JsonNode createVariantProduct(String name) throws Exception {
        int n = SEQ.incrementAndGet();
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("categoryId", categoryId);
        product.put("name", name);
        product.put("sku", "REV-V-" + n);
        product.put("price", new BigDecimal("12.50"));
        product.put("stockQuantity", 0);
        product.put("optionNames", List.of("Color"));
        product.put("variants", List.of(
                Map.of("options", List.of("Black"), "sku", "REV-V-" + n + "-BLK", "price", 12.5, "stockQuantity", 20),
                Map.of("options", List.of("Red"), "sku", "REV-V-" + n + "-RED", "price", 12.5, "stockQuantity", 20)));
        return data(mockMvc.perform(post("/api/v1/admin/products").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated()));
    }

    /** Cash on delivery, one of each product; returns the order id. */
    private long placeOrder(Shopper shopper, long... productIds) throws Exception {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (long productId : productIds) {
            lines.add(Map.of("productId", productId, "quantity", 1));
        }
        return checkout(shopper, lines);
    }

    private long placeVariantOrder(Shopper shopper, long productId, long variantId) throws Exception {
        return checkout(shopper, List.of(Map.of("productId", productId, "quantity", 1, "variantId", variantId)));
    }

    private long checkout(Shopper shopper, List<Map<String, Object>> lines) throws Exception {
        for (Map<String, Object> line : lines) {
            mockMvc.perform(post("/api/v1/cart/items").with(shopper.auth())
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(line)))
                    .andExpect(status().isOk());
        }
        return data(mockMvc.perform(post("/api/v1/orders/checkout").with(shopper.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"CASH_ON_DELIVERY\"}"
                                .formatted(shopper.addressId())))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private void setStatus(long orderId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(status));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
