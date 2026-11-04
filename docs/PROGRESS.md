# Suma Tracking: Progress and Decisions Log

## Current status (4 November 2026)
- Phase 1 (software installed): done
- Phase 2 (project setup): done
- Phase 3 (database and backend foundation): in progress
  - Backend connected to PostgreSQL: done (commit b145497)
  - Flyway V1, tables clients and projects: done
  - Next: add real clients/projects, then users and roles tables

## Database migrations (never edit a file after it has run)
| Version | File | Creates |
|---|---|---|
| V1 | V1__create_clients_and_projects.sql | clients, projects |

## How to start the backend
1. cd C:\Dev\SumaTracking\backend
2. $env:SUMA_DB_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "suma_app password" -AsSecureString)).Password
3. .\mvnw.cmd spring-boot:run
Stop with Ctrl + C, then Y. Repeat step 2 in every new terminal.

## Decisions
- The password is never stored in a file. It comes from the SUMA_DB_PASSWORD terminal variable.
- Only Flyway creates or changes tables (ddl-auto=validate).
- Times are stored in UTC and shown in India time (Asia/Kolkata).
- The app connects as suma_app, never as postgres.
- flyway_schema_history is Flyway's own record table. That is normal.