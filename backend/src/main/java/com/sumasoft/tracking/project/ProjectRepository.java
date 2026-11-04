package com.sumasoft.tracking.project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and saves projects in the database.
 * "EntityGraph" loads the client together with the project in one go (faster).
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = "client")
    List<Project> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "client")
    List<Project> findByClientIdOrderByIdAsc(Long clientId);

    /** Is this project name already used under this client? ("Hero" = "hero") */
    boolean existsByClientIdAndProjectNameIgnoreCase(Long clientId, String projectName);

    /** Same check, but ignoring the project being edited. */
    boolean existsByClientIdAndProjectNameIgnoreCaseAndIdNot(Long clientId, String projectName, Long id);
}