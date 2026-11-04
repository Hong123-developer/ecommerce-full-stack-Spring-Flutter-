package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.EmployeeRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeStatsResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Employee;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import com.ecomerceshopeonline.ecomercewepapp.exception.ConflictException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;
import com.ecomerceshopeonline.ecomercewepapp.mapper.EmployeeMapper;
import com.ecomerceshopeonline.ecomercewepapp.repository.EmployeeRepository;
import com.ecomerceshopeonline.ecomercewepapp.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String search, EmployeeStatus status, String department, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String cleanDepartment = (department != null && !department.isBlank()) ? department.trim() : null;
        return PageResponse.from(
                employeeRepository.search(cleanSearch, status, cleanDepartment, pageable),
                employeeMapper::toResponse
        );
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        return employeeMapper.toResponse(require(id));
    }

    @Override
    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String email = request.email().trim().toLowerCase();
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An employee with email '" + email + "' already exists");
        }
        Employee employee = employeeMapper.toEntity(request);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = require(id);
        String email = request.email().trim().toLowerCase();
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("An employee with email '" + email + "' already exists");
        }
        employeeMapper.apply(request, employee);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public EmployeeResponse updateStatus(Long id, EmployeeStatus status) {
        Employee employee = require(id);
        employee.setStatus(status);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Employee employee = require(id);
        employeeRepository.delete(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDepartments() {
        return employeeRepository.findDistinctDepartments();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeStatsResponse getStats() {
        long total = employeeRepository.count();
        long active = employeeRepository.countByStatus(EmployeeStatus.ACTIVE);
        long onLeave = employeeRepository.countByStatus(EmployeeStatus.ON_LEAVE);
        long inactive = employeeRepository.countByStatus(EmployeeStatus.INACTIVE);
        long terminated = employeeRepository.countByStatus(EmployeeStatus.TERMINATED);
        return new EmployeeStatsResponse(total, active, onLeave, inactive, terminated);
    }

    private Employee require(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));
    }
}
