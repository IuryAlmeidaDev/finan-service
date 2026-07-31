# Main Deployment Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that tests, publishes, and deploys the `main` branch using the `:main` container image.

**Architecture:** Create an independent GitHub Actions workflow rather than extending the development workflow. It will run Maven tests first, then publish `ghcr.io/iuryalmeidadev/finan-service:main` and deploy that exact tag to the existing VPS with the current secrets and health check.

**Tech Stack:** GitHub Actions, Maven/Temurin 21, GHCR, Docker Compose, appleboy/ssh-action.

---

### Task 1: Add the main deployment workflow

**Files:**
- Create: `.github/workflows/main-deploy.yml`
- Reference: `.github/workflows/develop-deploy.yml`

- [ ] **Step 1: Add the workflow definition with the main-only trigger**

```yaml
name: Finance Service - main

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read
  packages: write

env:
  IMAGE_NAME: ghcr.io/iuryalmeidadev/finan-service
```

- [ ] **Step 2: Add the test job before any deployment action**

```yaml
jobs:
  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Run tests
        run: mvn -B test
```

- [ ] **Step 3: Add the image publication and deployment job gated by tests**

```yaml
  build-and-deploy:
    name: Build and deploy
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ${{ env.IMAGE_NAME }}:main

      - name: Deploy and Verify on VPS
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/finan-service
            IMAGE_TAG=main docker compose pull app
            IMAGE_TAG=main docker compose up -d --remove-orphans
            docker compose ps
            code=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 10 http://127.0.0.1:8082/ || true)
            test "$code" != "000"
```

- [ ] **Step 4: Validate the file's key semantics locally**

Run:

```powershell
Get-Content .github/workflows/main-deploy.yml
rg -n "^      - main$|needs: test|:main|IMAGE_TAG=main|workflow_dispatch" .github/workflows/main-deploy.yml
```

Expected: the file contains the `main` trigger, a `needs: test` deployment gate, GHCR tag `:main`, VPS tag `IMAGE_TAG=main`, and manual dispatch.

- [ ] **Step 5: Commit the workflow**

```powershell
git add .github/workflows/main-deploy.yml
git commit -m "ci: add main deployment pipeline"
```

### Task 2: Integrate and observe the production workflow

**Files:**
- Modify: `.github/workflows/main-deploy.yml` only if GitHub reports a workflow syntax error

- [ ] **Step 1: Push the implementation branch and open a pull request to main**

```powershell
git push -u origin feat/main-deployment-pipeline
gh pr create --base main --head feat/main-deployment-pipeline --title "ci: add main deployment pipeline"
```

- [ ] **Step 2: Merge through the protected-main pull-request workflow**

```powershell
gh pr merge <PR_NUMBER> --merge --delete-branch
```

Expected: GitHub records a merge commit on `main` and starts `Finance Service - main`.

- [ ] **Step 3: Confirm the completed run**

```powershell
gh run list --branch main --workflow "Finance Service - main" --limit 1
```

Expected: the latest run is `completed` with `success`, including the Test and Build and deploy jobs.
