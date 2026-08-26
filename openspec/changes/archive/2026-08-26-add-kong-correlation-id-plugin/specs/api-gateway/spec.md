## MODIFIED Requirements

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (authentication, rate limiting, transformation, logging, or otherwise) as part of this capability, with the sole exception of the `correlation-id` plugin; the gateway otherwise performs proxying only.

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, or logged by any Kong plugin other than the correlation-id plugin

## ADDED Requirements

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
