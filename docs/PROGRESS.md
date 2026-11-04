# Suma Tracking: Progress and Decisions Log

## Current status (4 November 2026)
- Phase 1 (software installed): done
- Phase 2 (project setup): done
- Phase 3 (database and backend foundation): DONE
  - Backend connected to PostgreSQL (b145497)
  - Flyway V1 clients + projects (cb8575b), V2 initial data (acdac8c), V3 roles + users + teams (6e99b41)
  - Client API (0e1f9f7, 58c7065)
  - Project API (8094d46)
  - User API + roles (7ce39ee)
  - Project team API: done
- Next: Phase 4, login and permissions (Spring Security, password hashing, tokens)

## Roles (APPROVED 4 Nov 2026: follow the architecture diagram + FRD)
| Diagram user type | Roles (code) |
|---|---|
| Super Admin | SUPER_ADMIN, MANAGEMENT_VIEWER |
| Project Manager | CLIENT_ADMIN, PROJECT_MANAGER, TEAM_LEADER |
| Support User | L3_SUPPORT, L2_SUPPORT, L1_SUPPORT |

## APIs (no login yet; login and permissions come in Phase 4)
### Clients
GET /api/clients, GET /api/clients/{id}, POST /api/clients, PUT /api/clients/{id},
POST /api/clients/{id}/activate, POST /api/clients/{id}/deactivate

### Projects
GET /api/projects (?clientId=), GET /api/projects/{id}, POST /api/projects, PUT /api/projects/{id},
POST /api/projects/{id}/activate | deactivate | close

### Roles and users (employees)
GET /api/roles, GET /api/users, GET /api/users/{id}, POST /api/users, PUT /api/users/{id},
POST /api/users/{id}/activate | deactivate

### Project teams
GET /api/projects/{projectId}/team (?includeRemoved=true), PUT /api/projects/{projectId}/team,
POST /api/projects/{projectId}/team/{userId}/remove, GET /api/users/{userId}/projects

## Business rules (all tested 4 Nov 2026)
- Names unique (client name; project name per client; employee email and Employee ID), capital letters ignored.
- Nothing is ever deleted: clients/projects/users are deactivated or closed; team removal sets active = false.
- Projects cannot move to another client; no new projects under inactive clients.
- Reporting manager must be active; no self-reporting, no reporting loops.
- Teams: only ACTIVE projects and employees; Management Viewer cannot be on a team; PM/TL on a project must be Super Admin or Project Manager type; MEMBER needs L1/L2/L3; one active Project Manager per project; level can differ per project.

## Test data (kept on purpose, fake names, example.com emails)
- Client CL-0005 "Test Client Edited" (INACTIVE); project PR-0005 "Hero Test Project" (CLOSED)
- Employees TEST-001 Admin, TEST-002 Team Leader, TEST-003 L1 (INACTIVE), TEST-004 L2
- Hero team: TEST-001 PM, TEST-004 Member L2 (TEST-002 removed); Codility: TEST-004 Member L1

## Database migrations (never edit a file after it has run)
| Version | File | What it does |
|---|---|---|
| V1 | V1__create_clients_and_projects.sql | Creates clients and projects |
| V2 | V2__add_initial_clients_and_projects.sql | Adds Suma Tool, Hero, Codility (ZENDESK), SouthCo (KEYPANION) |
| V3 | V3__create_roles_users_and_project_assignments.sql | Creates roles (8), users, project_assignments; adds clients.account_manager_id |

## How to start the backend
1. cd C:\Dev\SumaTracking\backend
2. $env:SUMA_DB_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "suma_app password" -AsSecureString)).Password
3. .\mvnw.cmd spring-boot:run
Stop with Ctrl + C, then Y. Repeat step 2 in every new terminal.

## Decisions
- The password is never stored in a file. It comes from the SUMA_DB_PASSWORD terminal variable.
- Only Flyway creates or changes tables (ddl-auto=validate).
- Times are stored in UTC; each project has a time zone (default Asia/Kolkata).
- The app connects as suma_app, never as postgres.
- Client names are the same as the project names for now (user decision, 4 Nov 2026).
- Error answers never show internal details (ApiExceptionHandler).
- APPROVED 4 Nov 2026: audit logs go in the same PostgreSQL, in a separate protected schema (add-only).
- APPROVED 4 Nov 2026: external systems are read-only first (bring tickets in); writing back comes later.