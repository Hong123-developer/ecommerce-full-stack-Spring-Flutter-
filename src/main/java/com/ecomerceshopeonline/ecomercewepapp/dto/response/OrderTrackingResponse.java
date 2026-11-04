package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.DeliveryType;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.ShipmentStage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the customer's "Track order" screen shows: where the order is in its lifecycle
 * (plus the courier's step while it is shipped), who carries it, where it goes, who to call,
 * and every step so far.
 * <p>
 * The shipment fields are null until an admin fills them in, and on every order placed
 * before they existed; the delivery fields are null on orders from before delivery options.
 * The admin shipment endpoints answer with this same payload.
 */
public record OrderTrackingResponse(
        Long orderId,
        String orderNumber,
        OrderStatus currentStatus,
        /** The courier's step; only ever set while {@code currentStatus} is SHIPPED. */
        ShipmentStage shipmentStage,
        DeliveryType deliveryType,
        /** Snapshot of the chosen delivery option's name, e.g. "J&T Express". */
        String deliveryMethodName,
        /** When the store expects it to arrive. */
        LocalDateTime estimatedDeliveryAt,
        /** The delivery option's own estimate, e.g. "1-2 days". */
        String estimatedTime,
        String trackingNumber,
        String carrierName,
        /** Null while nothing about the courier is known (no name, phone or position). */
        Courier courier,
        /** Always present; its fields may be null (a pickup order has no map pin). */
        Destination destination,
        /** The store's contact details; null until the admin has saved the store settings. */
        Support support,
        /** Oldest first. */
        List<Event> events
) {

    /** The rider bringing the parcel and their last reported position. */
    public record Courier(String name, String phone, Double latitude, Double longitude,
                          LocalDateTime locationUpdatedAt) {
    }

    /** Where the order goes and who receives it, as captured at checkout. */
    public record Destination(String address, Double latitude, Double longitude,
                              String contactName, String contactPhone) {
    }

    public record Support(String phone, String email) {
    }

    /** One step of the timeline; {@code stage} is set on the courier's steps only. */
    public record Event(OrderStatus status, ShipmentStage stage, String note, LocalDateTime createdAt) {
    }
}
