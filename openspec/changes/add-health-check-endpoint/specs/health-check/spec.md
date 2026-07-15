## ADDED Requirements

### Requirement: Health Check Endpoint
The system SHALL provide a `GET /health` endpoint that returns a 200 status with a JSON body indicating the service is running.

#### Scenario: Service is running
- **WHEN** a client sends `GET /health`
- **THEN** the system returns a 200 status with a JSON body of `{"status": "UP"}`
