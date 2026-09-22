-- =====================================================
-- V7: Metas de ahorro
-- =====================================================
-- Una meta vive en una cuenta (su moneda es la de la cuenta). Varias metas pueden
-- compartir una cuenta, como sobres: lo ahorrado en cada una es la suma de sus aportes.

CREATE TABLE savings_goals (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    name            VARCHAR(60) NOT NULL,
    target_amount   NUMERIC(14,2) NOT NULL CHECK (target_amount > 0),
    target_date     DATE,
    color           VARCHAR(7),
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goal_user ON savings_goals(user_id);

-- Aportes (+) y retiros (−). Si vienen de/van a otra cuenta llevan su transferencia:
-- al borrar la transferencia se borra el aporte, así la meta nunca queda descuadrada.
CREATE TABLE goal_contributions (
    id                BIGSERIAL PRIMARY KEY,
    goal_id           BIGINT NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount            NUMERIC(14,2) NOT NULL CHECK (amount <> 0),
    contribution_date DATE NOT NULL,
    note              VARCHAR(200),
    transaction_id    BIGINT REFERENCES transactions(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contribution_goal ON goal_contributions(goal_id, contribution_date DESC);
