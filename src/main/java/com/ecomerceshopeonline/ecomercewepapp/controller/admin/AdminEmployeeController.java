package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.EmployeeRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeStatsResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import com.ecomerceshopeonline.ecomercewepapp.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ApiResponse<PageResponse<EmployeeResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) String department,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(employeeService.list(search, status, department, pageable));
    }

    @GetMapping("/stats")
    public ApiResponse<EmployeeStatsResponse> getStats() {
        return ApiResponse.ok(employeeService.getStats());
    }

    @GetMapping("/departments")
    public ApiResponse<List<String>> getDepartments() {
        return ApiResponse.ok(employeeService.getDepartments());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<EmployeeResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(employeeService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.ok("Employee created successfully", employeeService.create(request));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.ok("Employee updated successfully", employeeService.update(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public ApiResponse<EmployeeResponse> setStatus(
            @PathVariable Long id,
            @RequestParam EmployeeStatus status) {
        return ApiResponse.ok("Employee status updated", employeeService.updateStatus(id, status));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ApiResponse.message("Employee deleted successfully");
    }
}

