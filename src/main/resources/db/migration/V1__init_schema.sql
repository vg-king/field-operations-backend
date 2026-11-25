CREATE TABLE zones (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    region      VARCHAR(100)
);

CREATE TABLE teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    zone_id     BIGINT NOT NULL REFERENCES zones(id)
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    full_name      VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    phone          VARCHAR(20),
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    team_id        BIGINT REFERENCES teams(id),
    zone_id        BIGINT REFERENCES zones(id),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role_zone ON users(role, zone_id);

CREATE TABLE service_requests (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    location_address VARCHAR(300),
    zone_id          BIGINT NOT NULL REFERENCES zones(id),
    priority         VARCHAR(10) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    requested_by     BIGINT NOT NULL REFERENCES users(id),
    sla_due_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sr_status_zone ON service_requests(status, zone_id);

CREATE TABLE work_orders (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL UNIQUE REFERENCES service_requests(id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    priority      VARCHAR(10) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    zone_id       BIGINT NOT NULL REFERENCES zones(id),
    planned_by    BIGINT NOT NULL REFERENCES users(id),
    sla_due_at    TIMESTAMPTZ NOT NULL,
    completed_at  TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wo_status_zone ON work_orders(status, zone_id);
CREATE INDEX idx_wo_sla ON work_orders(sla_due_at);

CREATE TABLE work_order_assignments (
    id              BIGSERIAL PRIMARY KEY,
    work_order_id   BIGINT NOT NULL REFERENCES work_orders(id),
    technician_id   BIGINT NOT NULL REFERENCES users(id),
    assigned_by     BIGINT NOT NULL REFERENCES users(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);
-- DB-level safeguard: at most ONE active technician per work order
CREATE UNIQUE INDEX uq_active_assignment ON work_order_assignments(work_order_id) WHERE active;
CREATE INDEX idx_assign_tech_active ON work_order_assignments(technician_id, active);

CREATE TABLE activity_logs (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(30) NOT NULL,
    entity_id    BIGINT NOT NULL,
    action       VARCHAR(40) NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    actor_id     BIGINT,
    remarks      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_log_entity ON activity_logs(entity_type, entity_id, created_at);

CREATE TABLE alerts (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,
    severity        VARCHAR(10) NOT NULL,
    target_user_id  BIGINT REFERENCES users(id),
    zone_id         BIGINT REFERENCES zones(id),
    entity_type     VARCHAR(30),
    entity_id       BIGINT,
    message         VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ
);
CREATE INDEX idx_alert_target ON alerts(target_user_id, read_at);
