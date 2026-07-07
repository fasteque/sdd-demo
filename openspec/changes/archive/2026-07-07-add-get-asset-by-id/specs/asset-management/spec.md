## ADDED Requirements

### Requirement: Retrieve Asset by Id
The system SHALL provide a `GET /assets/{id}` endpoint that returns the asset matching the given id.

#### Scenario: Asset found
- **WHEN** a client sends `GET /assets/{id}` for an id that exists
- **THEN** the system returns the matching asset with a 200 status

#### Scenario: Asset not found
- **WHEN** a client sends `GET /assets/{id}` for an id that does not exist
- **THEN** the system returns a 404 status and does not return an asset body
