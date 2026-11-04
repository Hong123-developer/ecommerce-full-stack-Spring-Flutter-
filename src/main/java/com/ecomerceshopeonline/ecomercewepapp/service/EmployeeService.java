package com.ecomerceshopeonline.ecomercewepapp.service;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.EmployeeRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeStatsResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmployeeService {

    PageResponse<EmployeeResponse> list(String search, EmployeeStatus status, String department, Pageable pageable);

    EmployeeResponse getById(Long id);

    EmployeeResponse create(EmployeeRequest request);

    EmployeeResponse update(Long id, EmployeeRequest request);

    EmployeeResponse updateStatus(Long id, EmployeeStatus status);

    void delete(Long id);

    List<String> getDepartments();

    EmployeeStatsResponse getStats();
}
