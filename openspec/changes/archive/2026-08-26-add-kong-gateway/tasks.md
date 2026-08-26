## 1. Kong declarative config

- [x] 1.1 Create `kong/kong.yml` with `_format_version: "3.0"`, one service (`app`, pointing at `http://app:8080`), and one route (catch-all: `paths: ["/"]`, `strip_path: false`) attached to that service; verify the file is valid YAML and matches the shape in `design.md` - Decisions 1 and 4
- [x] 1.2 Verify no `plugins:` section exists anywhere in the file, per `proposal.md` - Non-goals

## 2. Compose stack wiring

- [x] 2.1 Check Docker Hub for the current latest stable `kong:3.x` OSS tag and add a `kong` service to `compose.app.yaml` using that pinned tag (not `latest`), per `design.md` - Goals/Non-Goals
- [x] 2.2 Configure the `kong` service's environment: `KONG_DATABASE=off`, `KONG_DECLARATIVE_CONFIG=/etc/kong/kong.yml`, `KONG_ADMIN_LISTEN=off`, per `design.md` - Decisions 1 and 3
- [x] 2.3 Bind-mount `./kong/kong.yml` read-only to `/etc/kong/kong.yml` in the `kong` service
- [x] 2.4 Publish the `kong` service's proxy port as `8080:8000` and add `depends_on: [app]` (plain form, no health condition), per `design.md` - Decisions 2 and 5
- [x] 2.5 Remove the `app` service's `ports: ['8080:8080']` mapping in `compose.app.yaml` so it's no longer published to the host, per `specs/api-gateway/spec.md` - "No Direct Host Access to the App Container"

## 3. Verification

- [x] 3.1 Run `docker compose -f compose.app.yaml up --build`, confirm all three containers (`kong`, `app`, `mongodb`) start successfully, and confirm `docker compose -f compose.app.yaml ps` shows no host port bound for `app`
- [x] 3.2 Send a request to `http://localhost:8080` (e.g. `GET /health`, matching the existing `health-check` capability) through Kong and verify it returns the same response the app would return directly, confirming end-to-end proxying works
- [x] 3.3 Confirm `http://localhost:8001` (Kong's default Admin API port) is unreachable from the host, verifying `KONG_ADMIN_LISTEN=off` took effect
- [x] 3.4 Run `docker compose -f compose.app.yaml down -v` and confirm a clean teardown with no orphaned containers or errors

## 4. Documentation

- [x] 4.1 Update `README.md`'s "3. Fully containerized (app + MongoDB)" section to mention requests now pass through Kong Gateway, and remove the now-inaccurate note that the app's own port is directly exposed
