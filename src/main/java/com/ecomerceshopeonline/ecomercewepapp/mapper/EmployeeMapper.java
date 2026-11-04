package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.EmployeeRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.EmployeeResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.Employee;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getPosition(),
                employee.getDepartment(),
                employee.getSalary(),
                employee.getHireDate(),
                employee.getStatus(),
                employee.getAvatarUrl(),
                employee.getAddress(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }

    public Employee toEntity(EmployeeRequest request) {
        return Employee.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim().toLowerCase())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .position(request.position().trim())
                .department(request.department() != null ? request.department().trim() : null)
                .salary(request.salary())
                .hireDate(request.hireDate())
                .status(request.status() != null ? request.status() : EmployeeStatus.ACTIVE)
                .avatarUrl(request.avatarUrl())
                .address(request.address() != null ? request.address().trim() : null)
                .build();
    }

    public void apply(EmployeeRequest request, Employee target) {
        target.setFirstName(request.firstName().trim());
        target.setLastName(request.lastName().trim());
        target.setEmail(request.email().trim().toLowerCase());
        target.setPhone(request.phone() != null ? request.phone().trim() : null);
        target.setPosition(request.position().trim());
        target.setDepartment(request.department() != null ? request.department().trim() : null);
        target.setSalary(request.salary());
        target.setHireDate(request.hireDate());
        if (request.status() != null) {
            target.setStatus(request.status());
        }
        target.setAvatarUrl(request.avatarUrl());
        target.setAddress(request.address() != null ? request.address().trim() : null);
    }
}
