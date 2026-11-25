# Suma Tracking

Suma Soft's internal support platform: tickets from every client tool in one place, SLA, automation,
role-based access, knowledge base and employee attendance.

Built with Java 21 (Spring Boot), React and PostgreSQL 17.

---

## Run it with Docker (easiest, Windows)

You need only **Docker Desktop**. Java, Node and PostgreSQL come inside Docker.

**1. Install Docker Desktop** (once). Open PowerShell and run:

```
winget install -e --id Docker.DockerDesktop
```

Restart the computer, open **Docker Desktop**, and wait until it says **Engine running**.

**2. Download this project**: on GitHub click **Code > Download ZIP**, then unzip it (for example to `C:\Dev`).

**3. Start it.** In PowerShell, go into the unzipped folder and run the start script:

```
cd C:\Dev\suma-tracking-main
powershell -ExecutionPolicy Bypass -File scripts\docker-start.ps1
```

- The first time takes 5 to 15 minutes.
- It asks you to **make a database password** (typed hidden, never saved in a file). Remember it.
- On a new database it asks for the **first Super Admin**: name, email and a temporary password.

**4. Open** http://localhost:3000 and sign in. You must change the temporary password at the first sign-in.

| What | Command |
|---|---|
| Stop (data is kept) | `powershell -ExecutionPolicy Bypass -File scripts\docker-stop.ps1` |
| Start again | `powershell -ExecutionPolicy Bypass -File scripts\docker-start.ps1` |
| Forgot the database password | `powershell -ExecutionPolicy Bypass -File scripts\docker-reset-db-password.ps1` |

Never run `docker compose down -v`. It deletes the whole database.

---

## Run it on Kubernetes (optional, for learning)

Uses the Kubernetes inside Docker Desktop. It is a separate copy with its own database.

1. Docker Desktop > **Settings > Kubernetes > Enable Kubernetes** (choose **kubeadm** if asked) > **Apply**.
   Wait until it shows **Kubernetes running**.
2. Run:

```
powershell -ExecutionPolicy Bypass -File scripts\k8s-start.ps1
```

3. Open http://localhost:30300

Stop: `powershell -ExecutionPolicy Bypass -File scripts\k8s-stop.ps1`

The Kubernetes files are in the `k8s` folder: database (StatefulSet with its own disk), backend (Deployment),
website (Deployment with 2 copies) and their Services.

---

## What runs where

| Part | Docker | Kubernetes |
|---|---|---|
| Website (nginx) | http://localhost:3000 | http://localhost:30300 |
| Backend (Spring Boot) | inside Docker only | inside Kubernetes only |
| Database (PostgreSQL 17) | `localhost:55432` for pgAdmin, user `suma_app` | inside Kubernetes only |

## Security notes

- No password is stored in this repository, in the images or in any file. The database password is typed
  when starting; on Kubernetes it is kept as a Kubernetes Secret.
- The app connects as `suma_app`, which is not a database superuser.
- Real company data (Excel exports) belongs in `private-data/`, which is never uploaded to GitHub.
- A new Docker or Kubernetes copy starts with an **empty** database: no tickets and no users from anyone else's computer.

## Developer setup without Docker

See `scripts/start-backend.ps1` and `scripts/start-frontend.ps1` (needs Java 21, Node 22 and PostgreSQL 17 installed).
