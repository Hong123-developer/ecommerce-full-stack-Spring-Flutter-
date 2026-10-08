package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.CustomerResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminCustomerService {

    PageResponse<CustomerResponse> list(String search, Boolean enabled, Pageable pageable);

    CustomerResponse get(Long customerId);

    CustomerResponse setEnabled(Long customerId, boolean enabled);

    /**
     * Permanently removes a customer who has never ordered, with their addresses, cart,
     * tokens and codes. Customers with orders keep their account (disable it instead) so
     * the sales history stays intact.
     *
     * @param actingAdminId the signed-in administrator, or null when unknown
     */
    void delete(Long customerId, Long actingAdminId);

    /** Signup counts per day for the registration-history view. */
    List<DashboardResponse.DailyCount> registrationHistory(int days);
}
