## ADDED Requirements

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
