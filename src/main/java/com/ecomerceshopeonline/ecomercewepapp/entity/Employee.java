package com.ecomerceshopeonline.ecomercewepapp.entity;

import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "employees",
        indexes = {
                @Index(name = "idx_employees_email", columnList = "email"),
                @Index(name = "idx_employees_department", columnList = "department"),
                @Index(name = "idx_employees_status", columnList = "status")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Employee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "position", nullable = false, length = 80)
    private String position;

    @Column(name = "department", length = 60)
    private String department;

    @Column(name = "salary", precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "address", length = 255)
    private String address;

    public String getFullName() {
        return ((firstName != null ? firstName : "") +
                (lastName != null && !lastName.isBlank() ? " " + lastName : "")).trim();
    }
}
