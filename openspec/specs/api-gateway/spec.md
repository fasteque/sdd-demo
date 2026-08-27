# API Gateway Specification

## Purpose

Provides a single, versioned entrypoint (Kong Gateway OSS, DB-less mode) that sits in front of the app service in the containerized dev stack, proxying all inbound traffic to it without altering request or response content.

## Requirements

### Requirement: Gateway Proxies All Traffic to the App
The system SHALL run Kong Gateway OSS in DB-less (declarative-config) mode as part of the containerized dev stack, configured via a version-controlled declarative config file defining exactly one Kong service (pointing at the app) and exactly two Kong routes proxying to that service: an unauthenticated route for `/health` and an authenticated route for the asset endpoints (`/assets`, `/assets/{id}`). A request whose path matches neither route SHALL NOT be forwarded to the app.

#### Scenario: Client request through the gateway
- **WHEN** a client sends any HTTP request (any method) to `/health` or an asset path, at the gateway's published port
- **THEN** the gateway forwards the request unmodified to the app service and returns the app's response unmodified to the client, once any applicable route-level policy (such as API key authentication) is satisfied

#### Scenario: Client request to an unmapped path
- **WHEN** a client sends a request to a path that is neither `/health` nor an asset path
- **THEN** the gateway returns its own 404 response and does not forward the request to the app

#### Scenario: Gateway config is declarative and versioned
- **WHEN** the containerized dev stack is started
- **THEN** Kong loads its routing configuration from the checked-in declarative config file rather than from a database or the Admin API

### Requirement: No Direct Host Access to the App Container
The system SHALL expose the app only through the gateway in the containerized dev stack; the app service's port SHALL NOT be published to the host directly.

#### Scenario: App is unreachable except through the gateway
- **WHEN** the containerized dev stack (`compose.app.yaml`) is running
- **THEN** the app is only reachable from the host via the gateway's published port, and the app container's own port is not bound to a host port

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (rate limiting, transformation, logging, or otherwise) as part of this capability, with the sole exceptions of the `correlation-id` plugin (applied globally), the `key-auth` plugin (applied only to the asset routes, per the API key authentication requirement below), and the `rate-limiting` plugin (applied only to the asset routes, per the rate limiting requirement below).

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway's `/health` route
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, or logged by any Kong plugin other than the correlation-id plugin

#### Scenario: Asset routes carry only the documented policies
- **WHEN** a client sends a request to an asset route
- **THEN** the only Kong plugins that may act on the request are `correlation-id`, `key-auth`, and `rate-limiting`

### Requirement: Asset Routes Require API Key Authentication
The system SHALL require a valid API key (enforced by Kong's `key-auth` plugin) on requests to the asset routes (`/assets`, `/assets/{id}`, any method). The `/health` route SHALL remain reachable without a key.

#### Scenario: Valid API key allows access
- **WHEN** a client sends a request to `/assets` or `/assets/{id}` with a valid API key
- **THEN** the gateway forwards the request to the app and returns the app's response unmodified

#### Scenario: Missing API key is rejected
- **WHEN** a client sends a request to `/assets` or `/assets/{id}` without an API key
- **THEN** the gateway rejects the request with a 401 status and does not forward it to the app

#### Scenario: Invalid API key is rejected
- **WHEN** a client sends a request to `/assets` or `/assets/{id}` with an API key that does not match any registered consumer
- **THEN** the gateway rejects the request with a 401 status and does not forward it to the app

#### Scenario: Health endpoint remains open
- **WHEN** a client sends `GET /health` with no API key
- **THEN** the gateway forwards the request to the app and returns the app's response unmodified

### Requirement: Asset Routes Are Rate Limited Per Consumer
The system SHALL apply Kong's `rate-limiting` plugin to the asset routes (`/assets`, `/assets/{id}`), configured for 5 requests per minute, counted per authenticated consumer (not globally, not per IP), using the `local` counting policy, with `hide_client_headers: true` so the plugin's own informational headers are not added to any response. The `/health` route SHALL NOT be rate limited.

#### Scenario: Requests within the limit are forwarded
- **WHEN** a consumer sends 5 or fewer requests to an asset route within a rolling one-minute window
- **THEN** the gateway forwards each request to the app and returns the app's response unmodified, with no rate-limit-related headers (`RateLimit-*`, `X-RateLimit-*`) added

#### Scenario: Exceeding the limit is rejected at the gateway
- **WHEN** a consumer sends more than 5 requests to an asset route within a rolling one-minute window
- **THEN** the gateway rejects the excess requests with a 429 status, a Kong-generated JSON body, and no rate-limit-related headers (`hide_client_headers: true` suppresses `Retry-After` and `RateLimit-*` on the 429 response too, not only on in-limit responses), and does not forward them to the app

#### Scenario: Limit is scoped per consumer
- **WHEN** two different consumers each send requests to an asset route
- **THEN** one consumer exceeding its 5-requests-per-minute limit does not affect the other consumer's ability to send requests

#### Scenario: Health endpoint is unaffected
- **WHEN** a client sends any number of requests to `/health` within a one-minute window
- **THEN** none of those requests are rejected due to rate limiting

### Requirement: API Key Value Is Not Committed to Version Control
The declarative Kong config committed to the repository SHALL NOT contain the literal API key value. It SHALL contain a placeholder in place of the key, resolved into the real value at container startup from an environment variable, with the real value supplied via a gitignored `.env` file.

#### Scenario: Committed config contains no secret
- **WHEN** the committed `kong/kong.yml` is inspected
- **THEN** it contains a placeholder for the API key credential, not a literal key value

#### Scenario: Real key supplied via gitignored file
- **WHEN** the containerized dev stack is started
- **THEN** the real API key value is supplied to Kong via a `.env` file that is excluded from version control by `.gitignore`

### Requirement: Gateway Attaches a Correlation ID to Every Request
The system SHALL apply Kong's `correlation-id` plugin globally (not scoped to any single service or route) so that every request passing through the gateway carries a correlation identifier in the `Request-Id` header, and SHALL echo that identifier back to the client on the response.

#### Scenario: Client request without a correlation ID
- **WHEN** a client sends a request to the gateway without a `Request-Id` header
- **THEN** the gateway generates a correlation ID, forwards the request to the app with a `Request-Id` header set to that value, and returns the same `Request-Id` header on the response to the client

#### Scenario: Client request with an existing correlation ID
- **WHEN** a client sends a request to the gateway that already includes a `Request-Id` header
- **THEN** the gateway preserves the client-supplied value, forwards it to the app, and echoes the same value back on the response to the client

#### Scenario: Correlation ID applies regardless of route
- **WHEN** a client sends a request to any route exposed by the gateway
- **THEN** the `Request-Id` header is attached to the request and response, not only for requests to the `app` service's route
