## ADDED Requirements

### Requirement: Delete Asset by Id
The system SHALL provide a `DELETE /assets/{id}` endpoint that removes the asset matching the given id.

#### Scenario: Successful deletion
- **WHEN** a client sends `DELETE /assets/{id}` for an id that exists
- **THEN** the system removes the asset from MongoDB and returns a 204 status with no body

#### Scenario: Asset not found
- **WHEN** a client sends `DELETE /assets/{id}` for an id that does not exist
- **THEN** the system returns a 404 status and does not modify any asset data
