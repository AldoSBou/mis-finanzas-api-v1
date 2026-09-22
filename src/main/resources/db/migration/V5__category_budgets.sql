-- =====================================================
-- V5: Presupuesto mensual por categoría
-- =====================================================
-- Límite de gasto por categoría, en la moneda base del usuario.
-- Vale para todos los meses (como en la mayoría de apps de presupuesto).

CREATE TABLE category_budgets (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    CONSTRAINT uk_category_budget UNIQUE (user_id, category_id)
);
