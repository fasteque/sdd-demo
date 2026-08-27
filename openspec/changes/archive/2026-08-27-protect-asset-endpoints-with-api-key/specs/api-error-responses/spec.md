## ADDED Requirements

### Requirement: Scope Is App-Originated Responses Only
This standard error envelope requirement SHALL apply to error responses the app itself produces. It SHALL NOT apply to responses returned by the gateway before a request reaches the app.

#### Scenario: Gateway-rejected request is out of scope
- **WHEN** the gateway rejects a request (for example, a missing/invalid API key, or a path that matches no configured route) before forwarding it to the app
- **THEN** the resulting error response is not required to conform to the standard error envelope defined by this spec

#### Scenario: App-originated error still conforms
- **WHEN** the app itself returns a 4xx or 5xx response for a request it received
- **THEN** that response conforms to the standard error envelope defined by this spec, regardless of whether the request passed through gateway-level policies first
