## 1. Secret handling

- [x] 1.1 Add `.env` to `.gitignore` and verify `git status` shows a real `.env` file (once created in 1.2) as untracked/ignored
- [x] 1.2 Create a local `.env` (gitignored) with a generated API key value (e.g. `KONG_ASSET_API_KEY=<random value>`), and a checked-in `.env.example` with the variable name and a placeholder value, and verify `git status` shows `.env.example` as a new tracked file but not `.env`

## 2. Kong declarative config

- [x] 2.1 In `kong/kong.yml`, replace the single catch-all route with two routes on the `app` service: an `health` route matching `/health` and an `assets` route matching `/assets` (with subpaths), and verify `docker compose -f compose.app.yaml config` (or equivalent Kong config validation) parses without error
- [x] 2.2 Add a Kong consumer (e.g. `asset-client`) with a `keyauth_credentials` entry whose `key` value is a placeholder (`__KONG_ASSET_API_KEY__`), not a real secret — `key-auth`'s `key` field is not a Kong-vault-referenceable field (verified empirically: `{vault://env/...}` is stored/matched literally, not resolved), so the placeholder is resolved at container startup instead (see 3.1) — and verify the committed `kong/kong.yml` contains no literal key value
- [x] 2.3 Attach the `key-auth` plugin to the `assets` route only (not globally, not on the `health` route), and verify the `correlation-id` plugin remains configured globally and unaffected

## 3. Compose wiring

- [x] 3.1 Mount `kong/kong.yml` as `/etc/kong/kong.yml.template` (not directly as `/etc/kong/kong.yml`), add `env_file: .env` to the `kong` service, and override its `command` to render the template into `/etc/kong/kong.yml` via `sed` substitution of `$KONG_ASSET_API_KEY` (escaped as `$$` in the compose file so Compose doesn't resolve it itself) before exec'ing the image's normal `docker-entrypoint.sh kong docker-start`; verify `docker compose -f compose.app.yaml config` shows the command and mount as expected

## 4. Verification

- [x] 4.1 Start the stack (`docker compose -f compose.app.yaml up`) and verify `GET /health` through the gateway returns 200 with no API key
- [x] 4.2 Verify `GET /assets` through the gateway returns 401 with no API key, and returns 200 with the correct key (via `apikey` header or query param, per Kong `key-auth` defaults)
- [x] 4.3 Verify `GET /assets/{id}` through the gateway returns 401 with a wrong/invalid API key
- [x] 4.4 Verify the `Request-Id` header is still present on both authenticated (assets) and unauthenticated (health) responses
- [x] 4.5 Verify a request to a path matching neither route (e.g. `GET /nope`) returns Kong's own 404 and is not forwarded to the app

## 5. Documentation

- [x] 5.1 Update the doc(s) describing how to run the containerized stack to mention creating `.env` from `.env.example` and supplying the API key on asset requests, and verify the doc renders correctly
