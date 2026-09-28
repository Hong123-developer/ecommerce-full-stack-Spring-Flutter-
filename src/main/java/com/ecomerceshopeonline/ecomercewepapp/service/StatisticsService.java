package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.response.DashboardResponse;

public interface StatisticsService {

    /** @param days size of the trailing window used for the "recent" figures and charts. */
    DashboardResponse dashboard(int days);
}
