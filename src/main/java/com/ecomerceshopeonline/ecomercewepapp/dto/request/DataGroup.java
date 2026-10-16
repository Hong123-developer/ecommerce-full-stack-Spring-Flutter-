package com.ecomerceshopeonline.ecomercewepapp.dto.request;

/**
 * What admin › Settings › "Delete data" can wipe, one checkbox each. Administrator accounts
 * are never part of any group.
 */
public enum DataGroup {
    /** Orders, their items and payments, and the notifications about them. */
    ORDERS,
    /** Customer accounts with their addresses, carts and sign-in state. Needs ORDERS while they have orders. */
    CUSTOMERS,
    /** Products (with variants and photos) and categories. Past orders keep their item snapshots. */
    CATALOG,
    /** Home-page banners and delivery options. Past orders keep their delivery snapshots. */
    CONTENT,
    /**
     * Store branding (name, logo, tagline, contact details - back to the built-in defaults) and
     * the bank account shown on the payment page.
     */
    SETTINGS
}
