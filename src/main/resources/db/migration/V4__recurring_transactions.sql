-- =====================================================
-- V4: Movimientos recurrentes
-- =====================================================
-- Plantillas que generan movimientos periódicos (sueldo, alquiler, suscripciones).
-- auto_create = TRUE: se registran solos al llegar la fecha.
-- auto_create = FALSE: quedan "pendientes" hasta que el usuario los confirma
-- (útil para montos variables como luz o agua).

CREATE TABLE recurring_transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE','TRANSFER')),
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    to_account_id   BIGINT REFERENCES accounts(id) ON DELETE CASCADE,
    category_id     BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    to_amount       NUMERIC(14,2) CHECK (to_amount IS NULL OR to_amount > 0),
    exchange_rate   NUMERIC(14,6) CHECK (exchange_rate IS NULL OR exchange_rate > 0),
    description     VARCHAR(200),
    frequency       VARCHAR(10) NOT NULL CHECK (frequency IN ('WEEKLY','MONTHLY','YEARLY')),
    -- Día del mes de referencia: un recurrente del 31 cae el 28/29 en febrero
    -- y vuelve al 31 en marzo.
    anchor_day      INT NOT NULL CHECK (anchor_day BETWEEN 1 AND 31),
    next_date       DATE NOT NULL,
    end_date        DATE,
    auto_create     BOOLEAN NOT NULL DEFAULT TRUE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_user_next ON recurring_transactions(user_id, next_date) WHERE active;

ALTER TABLE transactions
    ADD COLUMN recurring_id BIGINT REFERENCES recurring_transactions(id) ON DELETE SET NULL;
