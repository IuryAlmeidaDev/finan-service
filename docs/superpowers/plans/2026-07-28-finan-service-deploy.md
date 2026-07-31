# Finan Service Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Containerize the Finance Service and deploy the `develop` branch automatically to the Oracle VPS using GitHub Actions, while persisting PostgreSQL data and application attachments.

**Architecture:** GitHub Actions runs Maven tests, builds a Java 21 container image, and publishes it to GHCR tagged `develop`. The VPS runs the image with Docker Compose alongside a PostgreSQL 16 container; the workflow copies the Compose file and restarts only the Finance Service stack over SSH. The VPS does not need GitHub write credentials.

**Tech Stack:** Quarkus 3.37.3, Java 21, Maven Wrapper, Docker, Docker Compose, PostgreSQL 16, GitHub Actions, GHCR, Oracle VPS.

## Global Constraints

- Deploy only from the `develop` branch during this phase.
- Keep the existing `portainer`, `vikunja`, `software-house-site`, and `sorteador-java` containers running.
- Use VPS port `8082`; verify it is free before exposing it.
- Store PostgreSQL data and attachments in named Docker volumes.
- Do not put passwords, private keys, tokens, or database URLs with passwords in Git.
- Do not build Maven/Docker images on the 1 GB VPS; build in GitHub Actions.
- Do not create or select paid Oracle resources.

---

### Task 1: Create the production container image

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

- [ ] Add a multi-stage Dockerfile using Maven/Temurin 21 for build and Temurin 21 JRE for runtime.
- [ ] Copy the Quarkus fast-jar output from `target/quarkus-app` and expose port `8082`.
- [ ] Exclude `.git`, `target`, local IDE files, and secrets in `.dockerignore`.
- [ ] Verify locally with `./mvnw.cmd test` and `./mvnw.cmd package -DskipTests` using Java 21.

### Task 2: Define the VPS Compose stack

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/.env.example`

- [ ] Define `db` as `postgres:16-alpine` with a healthcheck, persistent `finance-db` volume, and variables from the VPS `.env`.
- [ ] Define `app` from `${IMAGE_REPOSITORY}:${IMAGE_TAG:-develop}` with port `8082:8082`.
- [ ] Configure `DB_URL=jdbc:postgresql://db:5432/finance_db`, `DB_USER`, `DB_PASSWORD`, and `ATTACHMENT_DIR=/app/attachments`.
- [ ] Persist `/app/attachments` in a named `finance-attachments` volume.
- [ ] Configure restart policies and dependency on the healthy database.
- [ ] Verify Compose interpolation and YAML structure without starting it locally.

### Task 3: Add the develop GitHub Actions pipeline

**Files:**
- Create: `.github/workflows/develop-deploy.yml`

- [ ] Trigger on pushes to `develop` and allow manual dispatch.
- [ ] Run the Maven test suite on Java 21.
- [ ] Log in to GHCR using the built-in `GITHUB_TOKEN` and publish `ghcr.io/iuryalmeidadev/finan-service:develop`.
- [ ] Use SSH secrets `VPS_HOST`, `VPS_USER`, and `VPS_SSH_KEY` to copy `deploy/docker-compose.yml` to `/opt/finan-service`.
- [ ] Run `docker compose pull` and `docker compose up -d --remove-orphans` only in `/opt/finan-service`.
- [ ] Add a post-deploy check for container state and `http://127.0.0.1:8082` from the VPS.
- [ ] Keep database credentials in the VPS `.env`, never in workflow YAML.

### Task 4: Validate and publish the deployment configuration

**Files:**
- Modify: `README.md`

- [ ] Document required GitHub Actions secrets and one-time VPS setup without exposing values.
- [ ] Run tests, Maven package, Dockerfile/Compose static checks, and inspect the final diff.
- [ ] Commit the deployment configuration on `develop`.
- [ ] Push `develop` to GitHub and verify the workflow is created and triggered.

### Task 5: Prepare the VPS runtime

- [ ] Confirm port `8082` is free and current containers remain healthy.
- [ ] Create `/opt/finan-service/.env` with a generated database password, restrictive permissions, and image repository settings.
- [ ] Authenticate the VPS to GHCR with a read-only package token only if the image is private; otherwise keep the image public and omit login.
- [ ] Start the Compose stack only after the workflow image exists.
- [ ] Verify app logs, database health, HTTP response, port reachability, and memory/disk usage.

