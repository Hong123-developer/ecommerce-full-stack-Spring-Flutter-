package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DataGroup;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DataSummaryResponse;

import java.util.Map;
import java.util.Set;

/**
 * Admin › Settings › Data: download a backup of all shop data, restore one, or permanently
 * delete groups of data. Every action re-checks the acting admin's password.
 */
public interface DataManagementService {

    DataSummaryResponse summary();

    /** Every shop table as rows of column -> value, plus format, version and counts. */
    Map<String, Object> backup(Long adminId, String password);

    /**
     * Replaces everything with {@code backup} in one transaction (all or nothing). Refresh
     * tokens are dropped, so everyone signs in again (with the passwords in the backup) once
     * their current short-lived access token runs out; a token whose id now belongs to a
     * different account stops working at once (see JwtAuthenticationFilter).
     *
     * @return rows restored per table
     */
    Map<String, Integer> restore(Long adminId, String password, String confirm, Map<String, Object> backup);

    /** @return rows deleted (or detached) per table */
    Map<String, Integer> delete(Long adminId, String password, String confirm, Set<DataGroup> groups);
}
