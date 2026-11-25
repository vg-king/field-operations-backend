# Field Operations Management System (backend)

Spring Boot 3 / Java 17 / PostgreSQL / Redis / Docker.
(Full README - problem understanding, architecture, trade-offs - is added in the docs phase.)

## Run
```bash
docker compose up --build
# API:     http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```
Demo users (password for all: `Password@123`):
`admin@ / planner@ / supervisor@ / tech1@ / tech2@ / tech3@ / requester@fieldops.com`

## Demo flow
1. `requester` -> `POST /api/v1/requests`
2. `planner`   -> `POST /api/v1/requests/{id}/triage`, then `POST /api/v1/work-orders {requestId}`
3. `planner`   -> `GET /api/v1/technicians` then `POST /api/v1/work-orders/{id}/assign {technicianId}`
4. `tech`      -> `PATCH /api/v1/work-orders/{id}/status` : ACCEPTED -> IN_PROGRESS -> COMPLETED
5. `supervisor`-> `POST /api/v1/work-orders/{id}/verify {approved:true}` then `/close`
6. anyone allowed -> `GET /api/v1/work-orders/{id}/history` (audit trail)

Watch the app logs for `[NOTIFY]` lines: every status change is published to Redis Pub/Sub.
