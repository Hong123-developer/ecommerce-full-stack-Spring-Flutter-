package com.ecomerceshopeonline.ecomercewepapp;

import com.ecomerceshopeonline.ecomercewepapp.config.AppProperties;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;
import com.ecomerceshopeonline.ecomercewepapp.entity.OrderItem;
import com.ecomerceshopeonline.ecomercewepapp.entity.Payment;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.service.CardPaymentGateway.CardCheckout;
import com.ecomerceshopeonline.ecomercewepapp.service.impl.PayWayCardGateway;
import com.ecomerceshopeonline.ecomercewepapp.util.PayWayHash;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signed purchase form in both display modes ({@code app.payway.view-type}). No HTTP:
 * signing the form never calls PayWay.
 */
class PayWayCardGatewayTest {

    private static final String KEY = "sk-UNIT-payway-secret";
    private static final String RETURN_TO = "https://shop.example.com/payment/42?from=card";

    @Test
    void popupIsTheDefaultAndReturnsToTheFramedPaymentPage() {
        for (String viewType : new String[]{null, "", "  ", "popup", " POPUP ", "something-else"}) {
            CardCheckout checkout = checkout(viewType);
            Map<String, String> fields = checkout.fields();

            String label = "view-type=" + viewType;
            assertThat(checkout.display()).as(label).isEqualTo(CardCheckout.DISPLAY_POPUP);
            assertThat(checkout.method()).isEqualTo("POST");
            assertThat(checkout.action())
                    .isEqualTo("https://checkout-sandbox.payway.test" + PayWayHash.PURCHASE_PATH);
            assertThat(fields.get("view_type")).as(label).isEqualTo("popup");
            assertThat(fields.get("continue_success_url")).isEqualTo(RETURN_TO + "&frame=1");
            assertThat(fields.get("cancel_url")).isEqualTo(RETURN_TO + "&frame=1&cancelled=1");
            assertSignedWithoutViewType(fields);

            List<String> names = new ArrayList<>(fields.keySet());
            assertThat(names.getLast()).isEqualTo("hash");
            assertThat(names.get(names.size() - 2)).isEqualTo("view_type");
        }
    }

    @Test
    void hostedViewKeepsTheSameTabFormUnchanged() {
        for (String viewType : new String[]{"hosted_view", " Hosted_View "}) {
            CardCheckout checkout = checkout(viewType);
            Map<String, String> fields = checkout.fields();

            assertThat(checkout.display()).as("view-type=" + viewType)
                    .isEqualTo(CardCheckout.DISPLAY_REDIRECT);
            assertThat(fields).doesNotContainKey("view_type");
            assertThat(fields.get("continue_success_url")).isEqualTo(RETURN_TO);
            assertThat(fields.get("cancel_url")).isEqualTo(RETURN_TO);
            assertSignedWithoutViewType(fields);
            assertThat(new ArrayList<>(fields.keySet()).getLast()).isEqualTo("hash");
        }
    }

    @Test
    void cardDetailsAreNeverPartOfTheForm() {
        Map<String, String> fields = checkout("popup").fields();
        assertThat(fields.keySet()).noneMatch(name -> name.contains("card") || name.contains("cvv")
                || name.contains("cvc") || name.contains("expir"));
        assertThat(String.join("|", fields.values())).doesNotContain(KEY);
    }

    /** The hash covers exactly the documented fields that were posted - view_type is not one. */
    private static void assertSignedWithoutViewType(Map<String, String> fields) {
        Map<String, String> signed = new LinkedHashMap<>(fields);
        signed.remove("hash");
        signed.remove("view_type");
        assertThat(signed.keySet()).isSubsetOf(PayWayHash.PURCHASE_HASH_FIELDS);
        assertThat(fields.get("hash")).isEqualTo(PayWayHash.purchaseHash(signed, KEY));

        StringBuilder b4hash = new StringBuilder();
        PayWayHash.PURCHASE_HASH_FIELDS.stream()
                .filter(signed::containsKey)
                .forEach(name -> b4hash.append(signed.get(name)));
        assertThat(fields.get("hash")).isEqualTo(PayWayHash.sign(b4hash.toString(), KEY));
    }

    private static CardCheckout checkout(String viewType) {
        AppProperties.Payway payway = new AppProperties.Payway(
                "https://checkout-sandbox.payway.test/", "ec_unit_01", KEY,
                "https://api.shop.example.com/api/v1/payments/payway/callback",
                "https://shop.example.com/", 30, viewType);
        AppProperties properties =
                new AppProperties(null, null, null, null, null, null, null, null, null, payway);
        PayWayCardGateway gateway = new PayWayCardGateway(properties, RestClient.builder());
        return gateway.createCheckout(order(), Payment.builder().currency("USD").build(),
                "4220261009123456");
    }

    private static Order order() {
        Order order = Order.builder()
                .id(42L)
                .orderNumber("ORD-20261009-0042")
                .user(User.builder().email("sok@example.com").build())
                .totalAmount(new BigDecimal("51.50"))
                .build();
        order.addItem(OrderItem.builder()
                .productName("Headphones")
                .quantity(2)
                .unitPrice(new BigDecimal("25.00"))
                .build());
        return order;
    }
}
