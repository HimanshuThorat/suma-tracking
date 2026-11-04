package com.sumasoft.tracking.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * The business rules for employees and roles live here.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderBySortOrderAsc().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        String email = request.email().trim();
        String code = request.employeeCode().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employee with this email already exists");
        }
        if (userRepository.existsByEmployeeCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employee with this Employee ID already exists");
        }
        checkShift(request);
        Role role = findRole(request.roleCode());
        AppUser manager = findManager(request.reportingManagerId(), null);
        AppUser saved = userRepository.saveAndFlush(AppUser.create(request, role, manager));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        AppUser user = findUser(id);
        String email = request.email().trim();
        String code = request.employeeCode().trim();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another employee already uses this email");
        }
        if (userRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another employee already uses this Employee ID");
        }
        checkShift(request);
        Role role = findRole(request.roleCode());
        AppUser manager = findManager(request.reportingManagerId(), id);
        user.applyDetails(request, role, manager);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse activateUser(Long id) {
        AppUser user = findUser(id);
        user.activate();
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse deactivateUser(Long id) {
        AppUser user = findUser(id);
        user.deactivate();
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private Role findRole(String roleCode) {
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role does not exist. See GET /api/roles for the list"));
    }

    /**
     * Checks the reporting manager: must exist, be ACTIVE, not be the employee,
     * and must not create a loop (A reports to B, B reports to A).
     */
    private AppUser findManager(Long managerId, Long employeeId) {
        if (managerId == null) {
            return null;
        }
        if (managerId.equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An employee cannot report to themselves");
        }
        AppUser manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reporting manager does not exist"));
        if (!"ACTIVE".equals(manager.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reporting manager is not active");
        }
        if (employeeId != null) {
            AppUser step = manager.getReportingManager();
            int guard = 0;
            while (step != null && guard < 100) {
                if (employeeId.equals(step.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "This would create a reporting loop (A reports to B and B reports to A)");
                }
                step = step.getReportingManager();
                guard++;
            }
        }
        return manager;
    }

    private void checkShift(UserRequest request) {
        if ((request.shiftStart() == null) != (request.shiftEnd() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Give both shift start and shift end, or neither");
        }
    }
}