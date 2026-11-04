package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CheckoutRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.CourierLocationRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateShipmentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderTrackingResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.InvoiceResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /** Turns the signed-in shopper's cart into an order, reserving stock as it goes. */
    OrderResponse checkout(Long userId, CheckoutRequest request);

    PageResponse<OrderSummaryResponse> listOwn(Long userId, Pageable pageable);

    OrderResponse getOwn(Long userId, Long orderId);

    OrderTrackingResponse trackingOwn(Long userId, Long orderId);

    InvoiceResponse invoiceOwn(Long userId, Long orderId);

    OrderResponse cancelOwn(Long userId, Long orderId);

    /**
     * Releases an order whose card payment window ran out: stock goes back, the order is
     * CANCELLED and the payment EXPIRED. Does nothing (returns false) unless the order is
     * still PENDING and its payment unpaid. Called by the payment flow, not by a controller.
     */
    boolean expireUnpaidOrder(Long orderId);

    // --- admin ---

    PageResponse<OrderSummaryResponse> adminList(OrderStatus status, String search, Pageable pageable);

    OrderResponse adminGet(Long orderId);

    OrderResponse adminUpdateStatus(Long orderId, OrderStatus status);

    /**
     * Replaces the courier details of an order (tracking number, carrier, courier, estimated
     * arrival, stage). A stage needs a SHIPPED delivery order; moving to a new stage adds a
     * timeline step and tells the customer. Answers with what the customer's tracking shows.
     */
    OrderTrackingResponse adminUpdateShipment(Long orderId, UpdateShipmentRequest request);

    /** Records where the courier of a SHIPPED order is now, for the customer's live map. */
    OrderTrackingResponse adminUpdateCourierLocation(Long orderId, CourierLocationRequest request);
}
