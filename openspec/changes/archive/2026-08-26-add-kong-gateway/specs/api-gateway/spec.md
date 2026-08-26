## Purpose

Provides a single, versioned entrypoint (Kong Gateway OSS, DB-less mode) that sits in front of the app service in the containerized dev stack, proxying all inbound traffic to it without altering request or response content.

## ADDED Requirements

### Requirement: Gateway Proxies All Traffic to the App
The system SHALL run Kong Gateway OSS in DB-less (declarative-config) mode as part of the containerized dev stack, configured via a version-controlled declarative config file defining exactly one Kong service (pointing at the app) and exactly one Kong route (a catch-all matching all paths and methods) that proxies to that service.

#### Scenario: Client request through the gateway
- **WHEN** a client sends any HTTP request (any path, any method) to the gateway's published port
- **THEN** the gateway forwards the request unmodified to the app service and returns the app's response unmodified to the client

#### Scenario: Gateway config is declarative and versioned
- **WHEN** the containerized dev stack is started
- **THEN** Kong loads its routing configuration from the checked-in declarative config file rather than from a database or the Admin API

### Requirement: No Direct Host Access to the App Container
The system SHALL expose the app only through the gateway in the containerized dev stack; the app service's port SHALL NOT be published to the host directly.

#### Scenario: App is unreachable except through the gateway
- **WHEN** the containerized dev stack (`compose.app.yaml`) is running
- **THEN** the app is only reachable from the host via the gateway's published port, and the app container's own port is not bound to a host port

### Requirement: No Traffic Policies Applied at the Gateway
The system SHALL NOT apply any Kong plugins (authentication, rate limiting, transformation, logging, or otherwise) as part of this capability; the gateway performs proxying only.

#### Scenario: Request passes through with no policy enforcement
- **WHEN** a client sends a request to the gateway
- **THEN** the request is proxied to the app without being rejected, transformed, rate-limited, or logged by any Kong plugin
