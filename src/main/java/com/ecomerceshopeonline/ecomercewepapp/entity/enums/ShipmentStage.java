package com.ecomerceshopeonline.ecomercewepapp.entity.enums;

/**
 * Where a shipped parcel is on the courier's side - the steps between "Shipped" and
 * "Delivered" on the customer's tracking timeline.
 * <p>
 * Only meaningful while an order is {@link OrderStatus#SHIPPED} and is a
 * {@link DeliveryType#DELIVERY} order: pickup orders never meet a courier, and moving the
 * order to any other status clears the stage.
 */
public enum ShipmentStage {

    /** The courier has collected the parcel from the store. */
    PICKED_UP,

    /** On its way between the courier's hubs. */
    IN_TRANSIT,

    /** With the rider for the last mile; arrives today. */
    OUT_FOR_DELIVERY
}
