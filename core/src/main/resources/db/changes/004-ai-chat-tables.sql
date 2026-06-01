--liquibase formatted sql

--changeset technokratos:004-ai-chat-sessions
CREATE TABLE ai_chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users (id),
    title VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

--changeset technokratos:004-ai-chat-messages
CREATE TABLE ai_chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES ai_chat_sessions (id),
    role VARCHAR(50) NOT NULL,
    content TEXT,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_messages_session ON ai_chat_messages (session_id, created_at);

--changeset technokratos:004-ai-chat-attachments
CREATE TABLE ai_message_attachments (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES ai_chat_messages (id),
    file_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    minio_url TEXT NOT NULL,
    mime_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL
);
