-- ============================================================
-- Enterprise RAG Platform — Database Schema V1
-- PostgreSQL with pgvector extension
-- ============================================================

-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- TENANTS — Multi-tenant isolation root
-- ============================================================
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Insert a default tenant for initial setup
INSERT INTO tenants (id, name, description)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Organization', 'Default tenant for the platform');

-- ============================================================
-- USERS — Authentication and authorization
-- ============================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER, ADMIN, SUPER_ADMIN
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(username, tenant_id)
);

-- Index for fast login lookups
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- ============================================================
-- DOCUMENTS — Uploaded file metadata
-- ============================================================
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    chunk_count INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADING',  -- UPLOADING, PROCESSING, READY, FAILED
    error_message TEXT,
    metadata JSONB DEFAULT '{}',
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_documents_tenant ON documents(tenant_id);
CREATE INDEX idx_documents_user ON documents(user_id);
CREATE INDEX idx_documents_status ON documents(status);

-- ============================================================
-- DOCUMENT_CHUNKS — Text segments with vector embeddings
-- ============================================================
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),  -- dimension matches nomic-embed-text; reconfigure for OpenAI (1536)
    token_count INT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- HNSW index for fast approximate nearest neighbor search
-- Using cosine distance operator for semantic similarity
CREATE INDEX idx_chunks_embedding_hnsw ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);
CREATE INDEX idx_chunks_tenant ON document_chunks(tenant_id);

-- ============================================================
-- CHAT_SESSIONS — Conversation containers
-- ============================================================
CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(500) DEFAULT 'New Chat',
    is_active BOOLEAN DEFAULT TRUE,
    message_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_sessions_user ON chat_sessions(user_id);
CREATE INDEX idx_sessions_tenant ON chat_sessions(tenant_id);

-- ============================================================
-- CHAT_MESSAGES — Individual messages within sessions
-- ============================================================
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,  -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    source_chunks JSONB DEFAULT '[]',  -- References to chunks used for context
    model_name VARCHAR(100),
    latency_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_messages_session ON chat_messages(session_id);
CREATE INDEX idx_messages_created ON chat_messages(created_at);

-- ============================================================
-- AUDIT_LOGS — Immutable audit trail
-- ============================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    user_id UUID,
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,  -- LOGIN, UPLOAD, QUERY, DELETE, etc.
    resource_type VARCHAR(100),    -- DOCUMENT, CHAT_SESSION, USER, etc.
    resource_id UUID,
    details JSONB DEFAULT '{}',
    ip_address VARCHAR(50),
    user_agent TEXT,
    status VARCHAR(20) DEFAULT 'SUCCESS',  -- SUCCESS, FAILURE
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_created ON audit_logs(created_at);

-- ============================================================
-- DOCUMENT_EMBEDDINGS — LangChain4j PgVector managed table
-- This table is auto-managed by LangChain4j's PgVectorEmbeddingStore
-- We create it here to ensure schema consistency
-- ============================================================
CREATE TABLE IF NOT EXISTS document_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    embedding vector(768),
    text TEXT,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_doc_embeddings_hnsw ON document_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
