-- changelog sql formatter

-- changeset korolev:0001

CREATE TABLE audit_events (
                              id UUID PRIMARY KEY,

                              timestamp TIMESTAMPTZ NOT NULL,

                              actor_type VARCHAR(50) NOT NULL,
                              actor_id VARCHAR(255),

                              realm_code VARCHAR(100),

                              action VARCHAR(100) NOT NULL,

                              resource_type VARCHAR(100),
                              resource_id VARCHAR(255),

                              status VARCHAR(50) NOT NULL,

                              ip VARCHAR(45),

                              user_agent TEXT,

                              metadata JSONB,

                              CONSTRAINT check_audit_events_action CHECK (
                                  action IN (
                                  'USER_CREATED', 'USER_ROLE_ASSIGNED',
                                  'REALM_CREATED', 'REALM_UPDATED', 'REALM_DELETED',
                                  'CLIENT_CREATED', 'CLIENT_SECRET_ROTATED',
                                  'MINIAPP_CREATED', 'MINIAPP_CONNECTED_TO_REALM', 'MINIAPP_PUBLISHED',
                                  'MINIAPP_STOPPED', 'MINIAPP_DELETED',
                                  'ROLE_MAPPING_CREATED',
                                  'LAUNCH_SUCCESS', 'LAUNCH_DENIED',
                                  'SSO_EXCHANGE_SUCCESS', 'SSO_EXCHANGE_FAILED',
                                  'SESSION_REVOKED'
                                  )
)
    );

CREATE INDEX idx_audit_realm_action
    ON audit_events(realm_code, action);
CREATE INDEX idx_audit_time
    ON audit_events(occurred_at DESC);