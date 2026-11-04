package com.ecomerceshopeonline.ecomercewepapp.config;

import com.ecomerceshopeonline.ecomercewepapp.entity.Employee;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import com.ecomerceshopeonline.ecomercewepapp.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class EmployeeSeeder implements ApplicationRunner {

    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0) {
            return;
        }

        log.info("Seeding initial employees data...");
        List<Employee> initialEmployees = List.of(
                Employee.builder()
                        .firstName("Dara")
                        .lastName("Sok")
                        .email("dara.sok@store.com")
                        .phone("+855 12 345 678")
                        .position("General Store Manager")
                        .department("Management")
                        .salary(new BigDecimal("1200.00"))
                        .hireDate(LocalDate.now().minusYears(2))
                        .status(EmployeeStatus.ACTIVE)
                        .address("Street 271, Phnom Penh, Cambodia")
                        .build(),
                Employee.builder()
                        .firstName("Bopha")
                        .lastName("Chan")
                        .email("bopha.chan@store.com")
                        .phone("+855 11 987 654")
                        .position("Inventory & Logistics Lead")
                        .department("Logistics")
                        .salary(new BigDecimal("850.00"))
                        .hireDate(LocalDate.now().minusYears(1).minusMonths(4))
                        .status(EmployeeStatus.ACTIVE)
                        .address("Tuol Kork, Phnom Penh, Cambodia")
                        .build(),
                Employee.builder()
                        .firstName("Vireak")
                        .lastName("Heng")
                        .email("vireak.heng@store.com")
                        .phone("+855 98 223 344")
                        .position("Senior Sales Associate")
                        .department("Sales")
                        .salary(new BigDecimal("650.00"))
                        .hireDate(LocalDate.now().minusMonths(9))
                        .status(EmployeeStatus.ACTIVE)
                        .address("Chamkarmon, Phnom Penh, Cambodia")
                        .build(),
                Employee.builder()
                        .firstName("Sreypov")
                        .lastName("Keo")
                        .email("sreypov.keo@store.com")
                        .phone("+855 77 445 566")
                        .position("Customer Support Specialist")
                        .department("Support")
                        .salary(new BigDecimal("500.00"))
                        .hireDate(LocalDate.now().minusMonths(5))
                        .status(EmployeeStatus.ACTIVE)
                        .address("Chbar Ampov, Phnom Penh, Cambodia")
                        .build(),
                Employee.builder()
                        .firstName("Rathana")
                        .lastName("Meng")
                        .email("rathana.meng@store.com")
                        .phone("+855 89 667 788")
                        .position("Store IT Support")
                        .department("IT")
                        .salary(new BigDecimal("750.00"))
                        .hireDate(LocalDate.now().minusMonths(3))
                        .status(EmployeeStatus.ON_LEAVE)
                        .address("Sen Sok, Phnom Penh, Cambodia")
                        .build()
        );

        employeeRepository.saveAll(initialEmployees);
        log.info("Successfully seeded {} employees.", initialEmployees.size());
    }
}

