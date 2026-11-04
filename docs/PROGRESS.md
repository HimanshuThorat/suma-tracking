# Suma Tracking: Progress and Decisions Log

## Current status (4 November 2026)
- Phase 1 (software installed): done
- Phase 2 (project setup): done
- Phase 3 (database and backend foundation): in progress
  - Backend connected to PostgreSQL: done (b145497)
  - Flyway V1 tables clients and projects: done (cb8575b)
  - Flyway V2 4 initial clients and projects: done (acdac8c)
  - Client API read: done (0e1f9f7)
  - Client API create / edit / activate / deactivate: done (58c7065)
  - Project API list / filter / create / edit / activate / deactivate / close: done
  - Flyway V3 roles (8), users, project_assignments, clients.account_manager_id: done
  - Next: User API (employees) and project team assignment

## Roles (APPROVED 4 Nov 2026: follow the architecture diagram + FRD)
| Diagram user type | Roles (code) |
|---|---|
| Super Admin | SUPER_ADMIN, MANAGEMENT_VIEWER |
| Project Manager | CLIENT_ADMIN, PROJECT_MANAGER, TEAM_LEADER |
| Support User | L3_SUPPORT, L2_SUPPORT, L1_SUPPORT |
- One main role per user (users.role_id).
- On each project a person is PROJECT_MANAGER, TEAM_LEADER or MEMBER, with support level L1/L2/L3 (can differ per project).
- Removing someone from a project sets active = false (history kept).

## Client API (no login yet; login and permissions come in Phase 4)
| Method | Address | What it does |
|---|---|---|
| GET | /api/clients | List all clients |
| GET | /api/clients/{id} | One client (404 if missing) |
| POST | /api/clients | Create (400 if input wrong, 409 if name exists) |
| PUT | /api/clients/{id} | Edit |
| POST | /api/clients/{id}/activate | Make ACTIVE |
| POST | /api/clients/{id}/deactivate | Make INACTIVE (never deletes) |

## Project API
| Method | Address | What it does |
|---|---|---|
| GET | /api/projects | List all projects |
| GET | /api/projects?clientId=3 | Projects of one client |
| GET | /api/projects/{id} | One project (404 if missing) |
| POST | /api/projects | Create (409 if client inactive or name exists under that client) |
| PUT | /api/projects/{id} | Edit (a project can never move to another client) |
| POST | /api/projects/{id}/activate | Make ACTIVE (client must be ACTIVE) |
| POST | /api/projects/{id}/deactivate | Make INACTIVE |
| POST | /api/projects/{id}/close | Make CLOSED (never deletes) |

Test data kept on purpose: client CL-0005 "Test Client Edited" (INACTIVE), project PR-0005 "Hero Test Project" (CLOSED).

## Database migrations (never edit a file after it has run)
| Version | File | What it does |
|---|---|---|
| V1 | V1__create_clients_and_projects.sql | Creates clients and projects |
| V2 | V2__add_initial_clients_and_projects.sql | Adds Suma Tool, Hero, Codility (ZENDESK), SouthCo (KEYPANION) |
| V3 | V3__create_roles_users_and_project_assignments.sql | Creates roles (8 filled in), users, project_assignments; adds clients.account_manager_id |

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
- Projects can never be moved to another client (protects ticket history).
- APPROVED 4 Nov 2026: audit logs go in the same PostgreSQL, in a separate protected schema (add-only).
- APPROVED 4 Nov 2026: external systems are read-only first (bring tickets in); writing back comes later.