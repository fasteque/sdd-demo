## MODIFIED Requirements

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

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (rate limiting, transformation, logging, or otherwise) as part of this capability, with the sole exceptions of the `correlation-id` plugin (applied globally) and the `key-auth` plugin (applied only to the asset routes, per the API key authentication requirement below).

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway's `/health` route
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, or logged by any Kong plugin other than the correlation-id plugin

#### Scenario: Asset routes carry only the documented policies
- **WHEN** a client sends a request to an asset route
- **THEN** the only Kong plugins that may act on the request are `correlation-id` and `key-auth`

## ADDED Requirements

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

### Requirement: API Key Value Is Not Committed to Version Control
The declarative Kong config committed to the repository SHALL NOT contain the literal API key value. It SHALL contain a placeholder in place of the key, resolved into the real value at container startup from an environment variable, with the real value supplied via a gitignored `.env` file.

#### Scenario: Committed config contains no secret
- **WHEN** the committed `kong/kong.yml` is inspected
- **THEN** it contains a placeholder for the API key credential, not a literal key value

#### Scenario: Real key supplied via gitignored file
- **WHEN** the containerized dev stack is started
- **THEN** the real API key value is supplied to Kong via a `.env` file that is excluded from version control by `.gitignore`
