## MODIFIED Requirements

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (rate limiting, transformation, logging, or otherwise) as part of this capability, with the sole exceptions of the `correlation-id` plugin (applied globally), the `key-auth` plugin (applied only to the asset routes, per the API key authentication requirement below), the `rate-limiting` plugin (applied only to the asset routes, per the rate limiting requirement below), and the `proxy-cache` plugin (applied only to the asset routes, per the response caching requirement below).

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway's `/health` route
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, cached, or logged by any Kong plugin other than the correlation-id plugin

#### Scenario: Asset routes carry only the documented policies
- **WHEN** a client sends a request to an asset route
- **THEN** the only Kong plugins that may act on the request are `correlation-id`, `key-auth`, `rate-limiting`, and `proxy-cache`

## ADDED Requirements

### Requirement: Asset Read Responses Are Cached at the Gateway
The system SHALL apply Kong's `proxy-cache` plugin to the asset routes (`/assets`, `/assets/{id}`), caching only `GET`/`HEAD` requests that receive a `200` response from the app, for 30 seconds, using the `memory` strategy (in-process, single Kong node). The `/health` route SHALL NOT be cached. Every response from an asset route SHALL carry Kong's `X-Cache-Status` header indicating cache state (`Hit`, `Miss`, `Bypass`, or `Refresh`).

#### Scenario: First request within the TTL window is a cache miss
- **WHEN** a consumer sends `GET /assets` (or `GET /assets/{id}`) and no cached entry exists for that request
- **THEN** the gateway forwards the request to the app, returns the app's response, and the response carries `X-Cache-Status: Miss`

#### Scenario: Repeated request within the TTL window is a cache hit
- **WHEN** a consumer sends the same `GET` request to an asset route again within 30 seconds of a prior cache miss for that same request
- **THEN** the gateway returns the cached response without forwarding the request to the app, and the response carries `X-Cache-Status: Hit`

#### Scenario: Cache entry expires after the TTL
- **WHEN** a consumer sends the same `GET` request to an asset route more than 30 seconds after the cached entry was stored
- **THEN** the gateway forwards the request to the app again rather than serving the expired cached response

#### Scenario: Writes are never cached
- **WHEN** a consumer sends `POST /assets` or `DELETE /assets/{id}`
- **THEN** the gateway forwards the request to the app and does not store or serve a cached response for it

#### Scenario: Writes do not invalidate previously cached reads
- **WHEN** a consumer sends `POST /assets` or `DELETE /assets/{id}` after a `GET` response for a related asset path has already been cached
- **THEN** a subsequent `GET` request within the original entry's remaining TTL MAY still return the pre-write cached response rather than reflecting the write

#### Scenario: Health endpoint is unaffected
- **WHEN** a client sends `GET /health`
- **THEN** the response is not cached and does not carry an `X-Cache-Status` header
