package com.sumasoft.tracking.team;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves project team memberships.
 */
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"user", "user.role", "project", "project.client"})
    List<ProjectAssignment> findByUserIdOrderByIdAsc(Long userId);

    Optional<ProjectAssignment> findByProjectIdAndUserId(Long projectId, Long userId);

    /** Is there already an active person with this project role (other than this employee)? */
    boolean existsByProjectIdAndProjectRoleAndActiveTrueAndUserIdNot(Long projectId, String projectRole, Long userId);
}