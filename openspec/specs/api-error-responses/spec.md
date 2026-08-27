# API Error Responses

## Purpose

Defines the platform-wide error response contract this API adopted from the `sdd-demo-api-contracts` reference store's `api-error-responses` spec: every 4xx/5xx response returns a structured `error` envelope (`code`, `message`, `traceId`, and `details` for validation failures) instead of an undocumented or empty body.

## Requirements

### Requirement: Standard error envelope
Every HTTP response this API returns with a 4xx or 5xx status code SHALL return a JSON body with a top-level `error` object containing `code`, `message`, and `traceId` string fields.

#### Scenario: Not-found error includes required error fields
- **WHEN** a client sends `GET /assets/{id}` for an id that does not exist
- **THEN** the response body is JSON containing a top-level `error` object with `code`, `message`, and `traceId` string fields

#### Scenario: Validation error includes required error fields
- **WHEN** a client sends `POST /assets` without a required field
- **THEN** the response body is JSON containing a top-level `error` object with `code`, `message`, and `traceId` string fields

### Requirement: Stable, machine-readable error codes
The `error.code` field SHALL be a SCREAMING_SNAKE_CASE string that is stable across releases and does not change when the human-readable `error.message` text changes.

#### Scenario: Asset-not-found error code is stable
- **WHEN** a client sends `GET /assets/{id}` or `DELETE /assets/{id}` for an id that does not exist
- **THEN** the response's `error.code` is `ASSET_NOT_FOUND`, regardless of the exact wording of `error.message`

#### Scenario: Validation error code is stable across validated fields
- **WHEN** a client sends a request that fails validation, whether from a missing required field, an invalid `page`/`size` value, or a blank `tags` entry
- **THEN** the response's `error.code` is `VALIDATION_FAILED` in every case, with the specific reason distinguished only in `error.details`

### Requirement: Field-level validation details
When a 400 response results from request validation failures, the `error.details` field SHALL be present as an array of objects, each with `field`, `code`, and `message`.

#### Scenario: Missing required field produces a field-level detail
- **WHEN** a client sends `POST /assets` without `name`, `type`, or `status`
- **THEN** the response's `error.details` array contains an entry for each missing field, each with `field`, `code`, and `message`

#### Scenario: Blank tag value produces a field-level detail
- **WHEN** a client sends `POST /assets` with a `tags` array containing a blank or whitespace-only entry
- **THEN** the response's `error.details` array contains an entry with `field` set to `tags`, plus `code` and `message`

#### Scenario: Invalid pagination parameter produces a field-level detail
- **WHEN** a client sends `GET /assets` with a negative `page` or a `size` less than 1
- **THEN** the response's `error.details` array contains an entry for the invalid parameter, each with `field`, `code`, and `message`

#### Scenario: Not-found error omits details
- **WHEN** a client sends `GET /assets/{id}` or `DELETE /assets/{id}` for an id that does not exist
- **THEN** the response's `error` object does not include a `details` field, since a not-found error is not a validation failure

### Requirement: HTTP status code is the sole status representation
The error response body SHALL NOT include a field that duplicates the HTTP status code.

#### Scenario: Error body omits duplicate status field
- **WHEN** this API returns any 4xx error response
- **THEN** the response body's `error` object does not contain a field repeating the numeric HTTP status code

### Requirement: Scope Is App-Originated Responses Only
This standard error envelope requirement SHALL apply to error responses the app itself produces. It SHALL NOT apply to responses returned by the gateway before a request reaches the app.

#### Scenario: Gateway-rejected request is out of scope
- **WHEN** the gateway rejects a request (for example, a missing/invalid API key, or a path that matches no configured route) before forwarding it to the app
- **THEN** the resulting error response is not required to conform to the standard error envelope defined by this spec

#### Scenario: App-originated error still conforms
- **WHEN** the app itself returns a 4xx or 5xx response for a request it received
- **THEN** that response conforms to the standard error envelope defined by this spec, regardless of whether the request passed through gateway-level policies first
