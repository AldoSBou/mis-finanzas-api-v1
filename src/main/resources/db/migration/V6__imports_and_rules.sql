-- =====================================================
-- V6: Importación de estados de cuenta y reglas de categorización
-- =====================================================

-- "Si la descripción contiene <pattern> → <categoría>"
CREATE TABLE categorization_rules (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern      VARCHAR(80) NOT NULL,
    category_id  BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rule_cat_user ON categorization_rules(user_id);

-- Cada importación agrupa sus movimientos para poder deshacerla completa
CREATE TABLE import_batches (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id   BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    file_name    VARCHAR(200),
    row_count    INT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_user ON import_batches(user_id, created_at DESC);

ALTER TABLE transactions
    ADD COLUMN import_batch_id BIGINT REFERENCES import_batches(id) ON DELETE SET NULL;

CREATE INDEX idx_tx_import_batch ON transactions(import_batch_id) WHERE import_batch_id IS NOT NULL;
