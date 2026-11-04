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
  - Next: users and roles tables (needs the role decision)

## Client API (no login yet; login and permissions come in Phase 4)
| Method | Address | What it does |
|---|---|---|
| GET | /api/clients | List all clients |
| GET | /api/clients/{id} | One client (404 if missing) |
| POST | /api/clients | Create (400 if input wrong, 409 if name exists) |
| PUT | /api/clients/{id} | Edit |
| POST | /api/clients/{id}/activate | Make ACTIVE |
| POST | /api/clients/{id}/deactivate | Make INACTIVE (never deletes) |
Tested 4 Nov 2026: create, edit, deactivate, 400 wrong input, 409 duplicate.

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
Tested 4 Nov 2026: 11 tests passed (list, filter, create, edit, close, inactive client 409, bad dates 400, bad time zone 400, move client 400, duplicate 409, count).

Test data kept on purpose: client CL-0005 "Test Client Edited" (INACTIVE), project PR-0005 "Hero Test Project" (CLOSED).

## Database migrations (never edit a file after it has run)
| Version | File | What it does |
|---|---|---|
| V1 | V1__create_clients_and_projects.sql | Creates the clients and projects tables |
| V2 | V2__add_initial_clients_and_projects.sql | Adds Suma Tool, Hero, Codility (ZENDESK), SouthCo (KEYPANION) |

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

## Proposals waiting for approval (from the architecture diagram, 4 Nov 2026)
- The 3 user types in the diagram (Super Admin, Project Manager, Support User) are the main groups. The 8 FRD roles fit under them.
- Audit logs go in the same PostgreSQL, in a separate protected schema (add-only).
- External systems are read-only first (bring tickets in). Writing back comes later.