package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.NotificationResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Order;

/**
 * In-app notifications about orders. The event methods run inside the caller's transaction,
 * so a rolled-back checkout or status change never leaves a notification behind.
 */
public interface NotificationService {

    // --- order events ---

    /** To every admin: a customer placed {@code order}. */
    void orderPlaced(Order order);

    /**
     * The order's payment settled. Always tells the customer; tells the admins too unless an
     * admin settled it by hand (they already know).
     */
    void paymentReceived(Order order, boolean notifyAdmins);

    /** To every admin: the customer cancelled {@code order} themselves. */
    void orderCancelledByCustomer(Order order);

    /** To the customer: an admin changed the status of {@code order} (to its current one). */
    void orderStatusChanged(Order order);

    /** To the customer: {@code order} was cancelled because it was never paid. */
    void orderExpired(Order order);

    // --- the signed-in user's own notifications ---

    PageResponse<NotificationResponse> listOwn(Long userId, int page, int size);

    long unreadCount(Long userId);

    NotificationResponse markRead(Long userId, Long notificationId);

    int markAllRead(Long userId);
}
