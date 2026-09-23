-- Extension vectorielle pour PostgreSQL (fournie par l'image pgvector/pgvector)
CREATE EXTENSION IF NOT EXISTS vector;

-- Table métier d'exemple exposée à l'agent via le "Transaction Tool".
-- Sert à démontrer qu'un agent peut, en plus du RAG, taper dans le relationnel classique.
CREATE TABLE IF NOT EXISTS transactions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      VARCHAR(64)      NOT NULL,
    label        VARCHAR(255)     NOT NULL,
    amount       NUMERIC(12, 2)   NOT NULL,
    currency     VARCHAR(3)       NOT NULL DEFAULT 'EUR',
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions (user_id, created_at DESC);

-- NB : la table `embeddings` (colonnes id, embedding vector, text, metadata jsonb)
-- est créée automatiquement par PgVectorEmbeddingStore au démarrage
-- (option createTable = true dans AiConfig).