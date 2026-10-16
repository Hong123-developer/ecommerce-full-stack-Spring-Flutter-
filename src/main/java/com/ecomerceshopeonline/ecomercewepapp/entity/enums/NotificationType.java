package com.ecomerceshopeonline.ecomercewepapp.entity.enums;

/**
 * What a notification is about. Who it is for decides the wording and the link: an admin's
 * goes to the admin order page, a customer's to their own order page.
 */
public enum NotificationType {
    /** To admins: a customer placed an order (paying online or at the store / on delivery). */
    NEW_ORDER,
    /** To admins and the customer: an online payment (KHQR, card) or a verified transfer settled. */
    PAYMENT_RECEIVED,
    /** To admins: the customer cancelled their own order. */
    ORDER_CANCELLED,
    /** To the customer: an admin moved their order on (confirmed, preparing, shipped/ready, ...). */
    ORDER_UPDATED,
    /** To the customer: the order was cancelled automatically because it was never paid. */
    ORDER_EXPIRED
}
