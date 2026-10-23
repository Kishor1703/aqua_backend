-- =========================================================
-- AquaAlert schema — V1
-- =========================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(150)  NOT NULL,
    email           VARCHAR(150)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    role            VARCHAR(20)   NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR')),
    phone_number    VARCHAR(20),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE areas (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    ward            VARCHAR(100) NOT NULL,
    zone            VARCHAR(100) NOT NULL,
    street          VARCHAR(150),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT       REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (name, ward)
);

CREATE TABLE residents (
    id                       BIGSERIAL PRIMARY KEY,
    resident_code            VARCHAR(30)  NOT NULL UNIQUE,
    house_number             VARCHAR(30)  NOT NULL,
    owner_name               VARCHAR(150) NOT NULL,
    phone_number             VARCHAR(20)  NOT NULL,
    alternate_phone_number   VARCHAR(20),
    area_id                  BIGINT       NOT NULL REFERENCES areas(id),
    notification_preference  VARCHAR(20)  NOT NULL DEFAULT 'SMS'
                                 CHECK (notification_preference IN ('SMS','VOICE','PUSH','WHATSAPP','ALL')),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                                 CHECK (status IN ('ACTIVE','INACTIVE')),
    fcm_token                VARCHAR(255),
    preferred_language       VARCHAR(10)  NOT NULL DEFAULT 'en',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_residents_area_id ON residents(area_id);
CREATE INDEX idx_residents_phone ON residents(phone_number);

CREATE TABLE water_schedules (
    id                  BIGSERIAL PRIMARY KEY,
    area_id             BIGINT      NOT NULL REFERENCES areas(id),
    release_date        DATE        NOT NULL,
    release_time        TIME        NOT NULL,
    notify_before_mins  INTEGER     NOT NULL DEFAULT 60,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                             CHECK (status IN ('SCHEDULED','RELEASED','DELAYED','CANCELLED')),
    notes               VARCHAR(500),
    created_by          BIGINT      NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_schedules_area_date ON water_schedules(area_id, release_date);
CREATE INDEX idx_schedules_status ON water_schedules(status);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    schedule_id     BIGINT      NOT NULL REFERENCES water_schedules(id) ON DELETE CASCADE,
    resident_id     BIGINT      NOT NULL REFERENCES residents(id),
    channel         VARCHAR(20) NOT NULL CHECK (channel IN ('SMS','VOICE','PUSH','WHATSAPP')),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SENT','FAILED','RETRYING')),
    provider_message_id VARCHAR(150),
    error_message   VARCHAR(500),
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_schedule_id ON notifications(schedule_id);
CREATE INDEX idx_notifications_resident_id ON notifications(resident_id);
CREATE INDEX idx_notifications_status ON notifications(status);

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    details         TEXT,
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
