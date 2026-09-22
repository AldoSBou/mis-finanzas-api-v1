-- =====================================================
-- V8: Tarjetas de crédito
-- =====================================================

-- Datos de la tarjeta (solo aplican a cuentas CREDIT_CARD)
ALTER TABLE accounts
    ADD COLUMN credit_limit   NUMERIC(14,2) CHECK (credit_limit IS NULL OR credit_limit > 0),
    ADD COLUMN statement_day  INT CHECK (statement_day BETWEEN 1 AND 31),
    ADD COLUMN due_day        INT CHECK (due_day BETWEEN 1 AND 31);

-- Estado de cuenta de un ciclo, con los montos que informa el banco
-- (el "pago del mes" depende de cuotas y sistema revolvente: no se calcula, se registra).
CREATE TABLE card_statements (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id       BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    closing_date     DATE NOT NULL,
    due_date         DATE NOT NULL,
    total_due        NUMERIC(14,2) NOT NULL CHECK (total_due >= 0),
    minimum_due      NUMERIC(14,2) CHECK (minimum_due IS NULL OR minimum_due >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_card_statement UNIQUE (account_id, closing_date),
    CONSTRAINT chk_statement_dates CHECK (due_date >= closing_date)
);

CREATE INDEX idx_statement_account ON card_statements(account_id, closing_date DESC);

-- Compra en cuotas: el gasto se registra completo en la fecha de compra (como en la deuda
-- total del banco); el plan dice cuánto se cobra cada mes y desde cuándo.
CREATE TABLE installment_plans (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id      BIGINT NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    installments        INT NOT NULL CHECK (installments BETWEEN 2 AND 60),
    installment_amount  NUMERIC(14,2) NOT NULL CHECK (installment_amount > 0),
    -- Mes (YYYY-MM) de la primera cuota facturada
    first_period        VARCHAR(7) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
