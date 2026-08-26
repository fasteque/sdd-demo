## 1. Gateway Config

- [x] 1.1 Add a top-level `plugins:` array to `kong/kong.yml` (sibling of `services:`, not nested under the `app` service) containing the `correlation-id` plugin with `config.header_name: Request-Id` and `config.echo_downstream: true`, and verify the file remains valid YAML and matches Kong's declarative config schema
- [x] 1.2 Start the containerized stack (`compose.app.yaml`) and verify Kong loads the config without error (check container logs for the gateway service)

## 2. Verification

- [x] 2.1 Send a request through the gateway without a `Request-Id` header and verify the app receives a `Request-Id` header and the client response also carries a `Request-Id` header with the same value
- [x] 2.2 Send a request through the gateway with a client-supplied `Request-Id` header and verify that exact value is forwarded to the app and echoed back on the response unchanged
- [x] 2.3 Confirm no other Kong plugin behavior (auth, rate limiting, transformation, logging) is present — the request/response body is otherwise unmodified
