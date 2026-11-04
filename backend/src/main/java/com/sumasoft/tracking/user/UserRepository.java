package com.sumasoft.tracking.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and saves employees in the database.
 */
public interface UserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = {"role", "reportingManager"})
    List<AppUser> findAllByOrderByIdAsc();

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);
}