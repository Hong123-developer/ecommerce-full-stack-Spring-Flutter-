package com.ecomerceshopeonline.ecomercewepapp.repository;

import com.ecomerceshopeonline.ecomercewepapp.entity.Employee;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
        SELECT e FROM Employee e
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.phone) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.position) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR e.status = :status)
          AND (:department IS NULL OR :department = '' OR LOWER(e.department) = LOWER(:department))
    """)
    Page<Employee> search(
            @Param("search") String search,
            @Param("status") EmployeeStatus status,
            @Param("department") String department,
            Pageable pageable
    );

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department <> '' ORDER BY e.department ASC")
    List<String> findDistinctDepartments();

    long countByStatus(EmployeeStatus status);
}
