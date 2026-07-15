# Health Check

## Purpose

Provides a lightweight liveness endpoint to confirm the service process is up and responding, without exercising any business endpoint or checking downstream dependencies (e.g. MongoDB connectivity).

## Requirements

### Requirement: Health Check Endpoint
The system SHALL provide a `GET /health` endpoint that returns a 200 status with a JSON body indicating the service is running.

#### Scenario: Service is running
- **WHEN** a client sends `GET /health`
- **THEN** the system returns a 200 status with a JSON body of `{"status": "UP"}`
