--liquibase formatted sql

--changeset kemalthes:2-miniapp-auth-schema
CREATE TABLE launch_tickets (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    client_id UUID NOT NULL,
    realm_mini_app_id UUID NOT NULL,
    mini_app_code VARCHAR(100) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    realm_roles VARCHAR(1000000),
    ticket_hash VARCHAR(128) NOT NULL UNIQUE,
    user_context VARCHAR(1000000),
    launch_context VARCHAR(1000000),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_launch_tickets_realm FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE,
    CONSTRAINT fk_launch_tickets_client FOREIGN KEY (client_id) REFERENCES realm_clients (id) ON DELETE CASCADE
);

CREATE TABLE miniapp_sessions (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    realm_mini_app_id UUID NOT NULL,
    mini_app_code VARCHAR(100) NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    realm_roles VARCHAR(1000000),
    refresh_token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_miniapp_sessions_realm FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE,
    CONSTRAINT ch_miniapp_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_launch_tickets_ticket_hash ON launch_tickets (ticket_hash);
CREATE INDEX idx_launch_tickets_realm_id ON launch_tickets (realm_id);
CREATE INDEX idx_launch_tickets_client_id ON launch_tickets (client_id);
CREATE INDEX idx_launch_tickets_realm_mini_app_id ON launch_tickets (realm_mini_app_id);
CREATE INDEX idx_launch_tickets_external_user_id ON launch_tickets (external_user_id);

CREATE INDEX idx_miniapp_sessions_refresh_token_hash ON miniapp_sessions (refresh_token_hash);
CREATE INDEX idx_miniapp_sessions_realm_external_user_status
    ON miniapp_sessions (realm_id, external_user_id, status);
CREATE INDEX idx_miniapp_sessions_realm_mini_app_id ON miniapp_sessions (realm_mini_app_id);
