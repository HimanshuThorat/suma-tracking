package com.sumasoft.tracking.project;

import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.client.Client;
import com.sumasoft.tracking.client.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * The business rules for projects live here.
 * Permissions (Step 25):
 *   see    : Super Admin, Management Viewer = all; everyone else = projects they are on
 *   create : Super Admin; Client Admin for their clients
 *   edit   : Super Admin; Client Admin of the client; the project's own Project Manager
 *   activate / deactivate / close : Super Admin; Client Admin of the client
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final AccessService access;

    public ProjectService(ProjectRepository projectRepository, ClientRepository clientRepository,
                          AccessService access) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.access = access;
    }

    /** All projects, or only the projects of one client when clientId is given (only those you may see). */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(Long clientId) {
        AuthenticatedUser me = access.current();
        List<Project> projects;
        if (access.seesEverything(me)) {
            projects = (clientId == null)
                    ? projectRepository.findAllByOrderByIdAsc()
                    : projectRepository.findByClientIdOrderByIdAsc(clientId);
        } else {
            Set<Long> ids = access.visibleProjectIds(me);
            projects = ids.isEmpty() ? List.of() : projectRepository.findByIdInOrderByIdAsc(ids);
            if (clientId != null) {
                projects = projects.stream().filter(p -> clientId.equals(p.getClient().getId())).toList();
            }
        }
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findVisibleProject(access.current(), id));
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        AuthenticatedUser me = access.current();
        if (!access.isSuperAdmin(me) && !access.isClientAdminOf(me, request.clientId())) {
            throw AccessService.forbidden();
        }
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client does not exist"));
        if (!"ACTIVE".equals(client.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add a project to an inactive client");
        }
        checkDatesAndTimeZone(request);
        String name = request.projectName().trim();
        if (projectRepository.existsByClientIdAndProjectNameIgnoreCase(client.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This client already has a project with this name");
        }
        Project saved = projectRepository.saveAndFlush(Project.create(client, request));
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        AuthenticatedUser me = access.current();
        Project project = findVisibleProject(me, id);
        if (!access.canManageProject(me, project)) {
            throw AccessService.forbidden();
        }
        if (!project.getClient().getId().equals(request.clientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A project cannot be moved to another client");
        }
        checkDatesAndTimeZone(request);
        String name = request.projectName().trim();
        if (projectRepository.existsByClientIdAndProjectNameIgnoreCaseAndIdNot(request.clientId(), name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This client already has a project with this name");
        }
        project.applyDetails(request);
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse activateProject(Long id) {
        Project project = findProjectForStatusChange(id);
        if (!"ACTIVE".equals(project.getClient().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Activate the client first. Its status is " + project.getClient().getStatus());
        }
        project.activate();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse deactivateProject(Long id) {
        Project project = findProjectForStatusChange(id);
        project.deactivate();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse closeProject(Long id) {
        Project project = findProjectForStatusChange(id);
        project.close();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    /** Status changes: Super Admin, or Client Admin of the project's client. */
    private Project findProjectForStatusChange(Long id) {
        AuthenticatedUser me = access.current();
        Project project = findVisibleProject(me, id);
        if (!access.isSuperAdmin(me) && !access.isClientAdminOf(me, project.getClient().getId())) {
            throw AccessService.forbidden();
        }
        return project;
    }

    /** Find a project the caller may see. Hidden projects answer "not found". */
    private Project findVisibleProject(AuthenticatedUser me, Long id) {
        if (!access.canSeeProject(me, id)) {
            throw AccessService.notFound("Project");
        }
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void checkDatesAndTimeZone(ProjectRequest request) {
        if (request.supportStartDate() != null && request.supportEndDate() != null
                && request.supportEndDate().isBefore(request.supportStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Support end date cannot be before the start date");
        }
        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            try {
                ZoneId.of(request.timeZone().trim());
            } catch (DateTimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Time zone is not valid (example: Asia/Kolkata)");
            }
        }
    }
}