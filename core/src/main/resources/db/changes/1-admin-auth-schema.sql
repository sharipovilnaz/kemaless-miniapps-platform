--liquibase formatted sql

--changeset kemalthes:1-admin-auth-schema
CREATE TABLE realms (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE platform_users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    global_role VARCHAR(100) NOT NULL,
    realm_id UUID,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_platform_users_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE SET NULL,
    CONSTRAINT ch_platform_users_global_role_check
        CHECK (global_role IN ('ROOT', 'PLATFORM_ADMIN', 'PLATFORM_AUDITOR')),
    CONSTRAINT ch_platform_users_role_realm_binding
        CHECK (
            (global_role = 'ROOT' AND realm_id IS NULL)
            OR (global_role <> 'ROOT' AND realm_id IS NOT NULL)
        )
);

CREATE TABLE realm_clients (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    client_id VARCHAR(150) NOT NULL,
    client_secret_hash VARCHAR(128) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_realm_clients_realm_client_id
        UNIQUE (realm_id, client_id),
    CONSTRAINT fk_realm_clients_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE CASCADE,
    CONSTRAINT ch_realm_clients_status
        CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE TABLE realm_roles (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_realm_roles_realm_code
        UNIQUE (realm_id, code),
    CONSTRAINT fk_realm_roles_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE CASCADE
);

CREATE TABLE external_role_mappings (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    external_role VARCHAR(150) NOT NULL,
    realm_role_id UUID NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_external_role_mappings_realm_external_role
        UNIQUE (realm_id, external_role),
    CONSTRAINT fk_external_role_mappings_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_external_role_mappings_realm_role
        FOREIGN KEY (realm_role_id) REFERENCES realm_roles (id)
        ON DELETE RESTRICT,
    CONSTRAINT ch_external_role_mappings_source
        CHECK (source IN ('HOST_ROLE'))
);

CREATE TABLE admin_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by_token_hash VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_admin_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES platform_users (id)
        ON DELETE CASCADE
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(255),
    realm_code VARCHAR(100),
    action VARCHAR(150) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    ip VARCHAR(100),
    user_agent VARCHAR(1000),
    metadata JSONB
);

CREATE INDEX idx_realms_code ON realms (code);
CREATE INDEX idx_platform_users_username ON platform_users (username);
CREATE INDEX idx_platform_users_global_role ON platform_users (global_role);
CREATE INDEX idx_platform_users_realm_id ON platform_users (realm_id);
CREATE INDEX idx_realm_clients_realm_id ON realm_clients (realm_id);
CREATE INDEX idx_realm_clients_client_id ON realm_clients (client_id);
CREATE INDEX idx_realm_clients_status ON realm_clients (status);
CREATE INDEX idx_realm_roles_realm_id ON realm_roles (realm_id);
CREATE INDEX idx_realm_roles_code ON realm_roles (code);
CREATE INDEX idx_external_role_mappings_realm_id ON external_role_mappings (realm_id);
CREATE INDEX idx_external_role_mappings_external_role ON external_role_mappings (external_role);
CREATE INDEX idx_external_role_mappings_realm_role_id ON external_role_mappings (realm_role_id);
CREATE INDEX idx_admin_refresh_tokens_user_id ON admin_refresh_tokens (user_id);
CREATE INDEX idx_admin_refresh_tokens_token_hash ON admin_refresh_tokens (token_hash);
CREATE INDEX idx_admin_refresh_tokens_status ON admin_refresh_tokens (status);
CREATE INDEX idx_audit_events_timestamp ON audit_events (timestamp);
CREATE INDEX idx_audit_events_actor ON audit_events (actor_type, actor_id);
CREATE INDEX idx_audit_events_realm_code ON audit_events (realm_code);
CREATE INDEX idx_audit_events_action ON audit_events (action);
