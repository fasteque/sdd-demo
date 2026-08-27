## MODIFIED Requirements

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (rate limiting, transformation, logging, or otherwise) as part of this capability, with the sole exceptions of the `correlation-id` plugin (applied globally), the `key-auth` plugin (applied only to the asset routes, per the API key authentication requirement below), and the `rate-limiting` plugin (applied only to the asset routes, per the rate limiting requirement below).

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway's `/health` route
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, or logged by any Kong plugin other than the correlation-id plugin

#### Scenario: Asset routes carry only the documented policies
- **WHEN** a client sends a request to an asset route
- **THEN** the only Kong plugins that may act on the request are `correlation-id`, `key-auth`, and `rate-limiting`

## ADDED Requirements

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
