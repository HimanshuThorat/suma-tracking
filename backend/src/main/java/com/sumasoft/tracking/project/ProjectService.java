package com.sumasoft.tracking.project;

import com.sumasoft.tracking.client.Client;
import com.sumasoft.tracking.client.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

/**
 * The business rules for projects live here.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;

    public ProjectService(ProjectRepository projectRepository, ClientRepository clientRepository) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
    }

    /** All projects, or only the projects of one client when clientId is given. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(Long clientId) {
        List<Project> projects = (clientId == null)
                ? projectRepository.findAllByOrderByIdAsc()
                : projectRepository.findByClientIdOrderByIdAsc(clientId);
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findProject(id));
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
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
        Project project = findProject(id);
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
        Project project = findProject(id);
        if (!"ACTIVE".equals(project.getClient().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Activate the client first. Its status is " + project.getClient().getStatus());
        }
        project.activate();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse deactivateProject(Long id) {
        Project project = findProject(id);
        project.deactivate();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse closeProject(Long id) {
        Project project = findProject(id);
        project.close();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    private Project findProject(Long id) {
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