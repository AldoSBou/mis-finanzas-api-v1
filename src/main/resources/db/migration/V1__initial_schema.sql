-- =====================================================
-- V1: Esquema inicial - Mis Finanzas
-- =====================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(80),
    currency_default VARCHAR(3) NOT NULL DEFAULT 'PEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(60) NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    default_bucket  VARCHAR(20) DEFAULT 'UNCATEGORIZED',
    color           VARCHAR(7),
    icon            VARCHAR(40),
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_category_user      ON categories(user_id);
CREATE INDEX idx_category_user_type ON categories(user_id, type);

CREATE TABLE transactions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id        BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    amount             NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    type               VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    transaction_date   DATE NOT NULL,
    description        VARCHAR(200),
    payment_method     VARCHAR(40),
    currency           VARCHAR(3) NOT NULL DEFAULT 'PEN',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ
);

-- Index crítico: la mayoría de queries filtran por user + rango de fechas
CREATE INDEX idx_tx_user_date     ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_tx_user_category ON transactions(user_id, category_id);

CREATE TABLE allocation_rules (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(60) NOT NULL,
    description     VARCHAR(250),
    percentages     JSONB NOT NULL,
    is_template     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rule_user ON allocation_rules(user_id);

CREATE TABLE monthly_budgets (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    year              INT NOT NULL CHECK (year BETWEEN 2000 AND 2100),
    month             INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    expected_income   NUMERIC(14,2) NOT NULL CHECK (expected_income >= 0),
    active_rule_id    BIGINT REFERENCES allocation_rules(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_budget_user_period UNIQUE (user_id, year, month)
);
