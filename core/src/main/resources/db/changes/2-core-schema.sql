--liquibase formatted sql

--changeset kemalthes:2-core-business-schema
CREATE TABLE mini_apps (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000000),
    base_url TEXT NOT NULL,
    default_entry_url TEXT,
    default_backend_url TEXT,
    entry_path TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    icon_url TEXT,
    theme_color VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ch_mini_apps_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT ch_mini_apps_code_format
        CHECK (code ~ '^[a-z0-9-]+$')
);

CREATE TABLE app_roles (
    app_id UUID NOT NULL,
    role_name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_app_roles PRIMARY KEY (app_id, role_name),
    CONSTRAINT fk_app_roles_mini_app
        FOREIGN KEY (app_id) REFERENCES mini_apps (id)
        ON DELETE CASCADE
);

CREATE TABLE observed_external_roles (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    external_role VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    seen_count BIGINT NOT NULL,
    CONSTRAINT fk_observed_external_roles_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_observed_external_role_realm_source UNIQUE (realm_id, external_role, source),
    CONSTRAINT ch_observed_external_roles_source
        CHECK (source IN ('HOST_ROLE')),
    CONSTRAINT ch_observed_external_roles_status
        CHECK (status IN ('UNMAPPED', 'MAPPED', 'IGNORED')),
    CONSTRAINT ch_observed_external_roles_seen_count
        CHECK (seen_count >= 0)
);

CREATE TABLE realm_mini_apps (
    id UUID PRIMARY KEY,
    realm_id UUID NOT NULL,
    mini_app_id UUID NOT NULL,
    entry_url TEXT NOT NULL,
    backend_url TEXT,
    status VARCHAR(50) NOT NULL,
    access_type VARCHAR(50) NOT NULL,
    role_match_mode VARCHAR(20),
    launches BIGINT NOT NULL DEFAULT 0,
    settings JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_realm_mini_apps_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_realm_mini_apps_mini_app
        FOREIGN KEY (mini_app_id) REFERENCES mini_apps (id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_realm_mini_apps_realm_mini_app UNIQUE (realm_id, mini_app_id),
    CONSTRAINT ch_realm_mini_apps_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'STOPPED', 'DELETED')),
    CONSTRAINT ch_realm_mini_apps_access_type
        CHECK (access_type IN ('PUBLIC_IN_REALM', 'AUTHENTICATED', 'ROLE_BASED')),
    CONSTRAINT ch_realm_mini_apps_role_match_mode
        CHECK (role_match_mode IS NULL OR role_match_mode IN ('ANY', 'ALL')),
    CONSTRAINT ch_realm_mini_apps_launches
        CHECK (launches >= 0)
);

CREATE TABLE realm_miniapp_roles (
    realm_miniapp_id UUID NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_realm_miniapp_roles PRIMARY KEY (realm_miniapp_id, role_code),
    CONSTRAINT fk_realm_miniapp_roles_realm_mini_app
        FOREIGN KEY (realm_miniapp_id) REFERENCES realm_mini_apps (id)
        ON DELETE CASCADE
);

CREATE TABLE miniapp_access_policies (
    id UUID PRIMARY KEY,
    realm_mini_app_id UUID NOT NULL UNIQUE,
    access_type VARCHAR(50) NOT NULL,
    role_match_mode VARCHAR(20),
    required_roles JSONB,
    required_groups JSONB,
    required_attributes JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_miniapp_access_policies_realm_mini_app
        FOREIGN KEY (realm_mini_app_id) REFERENCES realm_mini_apps (id)
        ON DELETE CASCADE,
    CONSTRAINT ch_miniapp_access_policies_access_type
        CHECK (access_type IN ('PUBLIC_IN_REALM', 'AUTHENTICATED', 'ROLE_BASED')),
    CONSTRAINT ch_miniapp_access_policies_role_match_mode
        CHECK (role_match_mode IS NULL OR role_match_mode IN ('ANY', 'ALL'))
);

CREATE INDEX idx_mini_apps_code ON mini_apps (code);
CREATE INDEX idx_mini_apps_status ON mini_apps (status);

CREATE INDEX idx_observed_external_roles_realm_id ON observed_external_roles (realm_id);
CREATE INDEX idx_observed_external_roles_status ON observed_external_roles (status);

CREATE INDEX idx_realm_mini_apps_realm_id ON realm_mini_apps (realm_id);
CREATE INDEX idx_realm_mini_apps_mini_app_id ON realm_mini_apps (mini_app_id);
CREATE INDEX idx_realm_mini_apps_status ON realm_mini_apps (status);

CREATE INDEX idx_miniapp_access_policies_realm_mini_app_id ON miniapp_access_policies (realm_mini_app_id);
