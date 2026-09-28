package com.ecomerceshopeonline.ecomercewepapp.entity.enums;

/**
 * Lifecycle of an order, from checkout through fulfilment.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
