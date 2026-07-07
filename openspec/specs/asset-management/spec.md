# Asset Management

## Purpose

TBD - capability for creating and managing assets in the system.

## Requirements

### Requirement: Create Asset
The system SHALL provide a `POST /assets` endpoint that accepts a name, type, tags, and status, and persists the asset to MongoDB.

#### Scenario: Successful asset creation
- **WHEN** a client sends `POST /assets` with a valid name, type, tags, and status
- **THEN** the system persists a new asset document in MongoDB and returns the created asset, including its generated id, with a 201 status

#### Scenario: Missing required field
- **WHEN** a client sends `POST /assets` without a name, type, or status
- **THEN** the system rejects the request with a 400 status and does not persist any asset

#### Scenario: Tags are optional
- **WHEN** a client sends `POST /assets` with a valid name, type, and status but no tags
- **THEN** the system persists the asset with an empty tag list and returns it with a 201 status

### Requirement: Asset Data Model
Each asset SHALL be stored with the following fields: a system-generated unique id, a name, a type, a list of tags, and a status.

#### Scenario: Asset stored with all fields
- **WHEN** an asset is persisted
- **THEN** the stored document contains `id`, `name`, `type`, `tags`, and `status` fields

### Requirement: Retrieve Asset by Id
The system SHALL provide a `GET /assets/{id}` endpoint that returns the asset matching the given id.

#### Scenario: Asset found
- **WHEN** a client sends `GET /assets/{id}` for an id that exists
- **THEN** the system returns the matching asset with a 200 status

#### Scenario: Asset not found
- **WHEN** a client sends `GET /assets/{id}` for an id that does not exist
- **THEN** the system returns a 404 status and does not return an asset body
