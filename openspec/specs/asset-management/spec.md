# Asset Management

## Purpose

Manages the lifecycle of assets in the system — creating new assets, retrieving them individually, and listing them with pagination.
Assets are the core domain entity this API exposes, each identified by a system-generated id and described by a name, type, tags, and status.

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

### Requirement: List Assets
The system SHALL provide a `GET /assets` endpoint that returns a paginated page of assets, ordered by insertion order, along with pagination metadata (page number, page size, total elements, and total pages).

#### Scenario: Default pagination
- **WHEN** a client sends `GET /assets` without `page` or `size` query parameters
- **THEN** the system returns the first page of assets (page `0`) with a default page size of `20` and a 200 status

#### Scenario: Explicit pagination
- **WHEN** a client sends `GET /assets?page=1&size=5`
- **THEN** the system returns the second page of assets (page `1`) with up to 5 assets and a 200 status

#### Scenario: Page beyond available data
- **WHEN** a client sends `GET /assets` with a `page` value beyond the last available page
- **THEN** the system returns an empty asset list with a 200 status and accurate pagination metadata

#### Scenario: Invalid page value
- **WHEN** a client sends `GET /assets` with a negative `page` value
- **THEN** the system rejects the request with a 400 status and does not return an asset list

#### Scenario: Invalid size value
- **WHEN** a client sends `GET /assets` with a `size` value less than 1
- **THEN** the system rejects the request with a 400 status and does not return an asset list
