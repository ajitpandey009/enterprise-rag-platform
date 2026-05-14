# Architecture Decision Records (ADR)

This document explains the key engineering decisions and trade-offs made during the development of the Enterprise RAG Platform.

## 1. Vector Database: PostgreSQL + pgvector vs. Pinecone/Weaviate
**Decision:** We chose PostgreSQL with the `pgvector` extension instead of a managed vector database like Pinecone or Weaviate.
**Rationale:**
- **Cost & Complexity:** Adding a dedicated vector database increases infrastructure complexity and cost. Since the application already needs a relational database for users, tenants, and audit logs, using Postgres keeps the stack simple (one database to manage).
- **ACID Compliance:** We can perform transactions that involve both relational metadata (e.g., updating document status) and vector embeddings simultaneously.
- **Performance:** For enterprise use cases with fewer than 10 million embeddings, `pgvector` with HNSW indexing provides near-identical retrieval latency to dedicated vector databases.

## 2. LLM Framework: LangChain4j vs. Python Microservice
**Decision:** We used `LangChain4j` directly inside the Spring Boot application instead of building a separate Python microservice for AI orchestration.
**Rationale:**
- **Simplicity:** A single Java backend is easier to deploy, monitor, and scale than a distributed dual-language architecture.
- **Enterprise Java:** Java provides robust concurrency (Virtual Threads), strict typing, and deep integrations with enterprise systems. LangChain4j brings the AI capabilities of LangChain without forcing a language switch.

## 3. Multi-Tenancy: ThreadLocal Context vs. Database Segregation
**Decision:** We implemented logical multi-tenancy using a `tenant_id` column and a `ThreadLocal` TenantContext, rather than separate databases or schemas per tenant.
**Rationale:**
- **Scalability:** Logical multi-tenancy scales easily to thousands of tenants without database overhead.
- **Security:** The `JwtAuthenticationFilter` intercepts requests, extracts the tenant ID from the signed token, and sets it in the ThreadLocal context. All database queries and vector searches strictly filter by this ID, ensuring robust data isolation.

## 4. Response Delivery: SSE (Server-Sent Events) vs. WebSockets
**Decision:** We used SSE for streaming AI responses instead of WebSockets.
**Rationale:**
- **Unidirectional Flow:** The LLM response stream is unidirectional (server to client). SSE is designed exactly for this, running over standard HTTP/1.1 and HTTP/2.
- **Firewall Friendly:** SSE doesn't require protocol upgrades like WebSockets, making it significantly easier to deploy in strict corporate network environments.

## 5. Model Deployment: Ollama (Local) vs. OpenAI (Cloud)
**Decision:** The platform defaults to running local open-source models (`llama3` and `nomic-embed-text`) via Ollama, while supporting OpenAI through profile configuration.
**Rationale:**
- **Data Privacy:** Enterprise internal documents (runbooks, API keys, source code) cannot always be sent to 3rd-party APIs like OpenAI due to compliance and security constraints. Local execution ensures zero data leakage.
- **Cost:** Running inference locally eliminates per-token API costs, allowing infinite document processing scale.
