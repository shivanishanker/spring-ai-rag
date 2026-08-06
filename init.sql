CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS alert_embeddings (
    alert_id uuid PRIMARY KEY REFERENCES cc_alerts_alert(id) ON DELETE CASCADE,
    embedding_event vector NOT NULL,
    embedding_risk vector NOT NULL,
    embedding_action vector NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
