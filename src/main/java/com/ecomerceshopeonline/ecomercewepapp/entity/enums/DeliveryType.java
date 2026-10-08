package com.ecomerceshopeonline.ecomercewepapp.entity.enums;

/**
 * How an order reaches the shopper - the answer to "how do you want to receive it?".
 */
public enum DeliveryType {

    /** A courier or the store's own rider brings it to a saved address. */
    DELIVERY,

    /** The shopper collects it at the store, so no address is needed. */
    PICKUP
}
