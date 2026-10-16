-- =====================================================
-- V11: Compromisos de pago y periodo del usuario
-- =====================================================
-- - Un compromiso es dinero que vas a tener que soltar en una fecha: alquiler, colegio,
--   una deuda puntual, la deuda con la que llega una tarjeta. No es un movimiento todavía;
--   se resta de "te quedan" hasta que se paga.
-- - period_start_day: si cobras el 25, tu mes va del 25 al 24. Nulo = mes calendario.

ALTER TABLE users ADD COLUMN period_start_day INTEGER
    CHECK (period_start_day IS NULL OR period_start_day BETWEEN 1 AND 28);

CREATE TABLE commitments (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                 VARCHAR(80) NOT NULL,
    -- En la moneda base del usuario
    amount               NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    due_date             DATE NOT NULL,
    -- Gasto que se registra al pagar (obligatoria si no es pago a una cuenta)
    category_id          BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    -- Cuenta sugerida desde donde se paga
    account_id           BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    -- Si se paga a una cuenta propia (deuda de tarjeta): el pago es "mover dinero", no un gasto
    to_account_id        BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    note                 VARCHAR(200),
    status               VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','PAID','SKIPPED')),
    paid_transaction_id  BIGINT REFERENCES transactions(id) ON DELETE SET NULL,
    paid_at              DATE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_commitments_user ON commitments(user_id, status, due_date);
