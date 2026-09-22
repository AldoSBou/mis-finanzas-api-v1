-- =====================================================
-- V3: Cuentas, transferencias y multi-moneda
-- =====================================================
-- - Cada movimiento pertenece a una cuenta (efectivo, banco, tarjeta, Yape...).
-- - Nuevo tipo TRANSFER: mueve dinero entre cuentas sin contar como ingreso/gasto.
-- - amount_base: monto convertido a la moneda base del usuario. Los reportes
--   suman siempre esta columna, nunca mezclan monedas.

CREATE TABLE accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(60) NOT NULL,
    type             VARCHAR(20) NOT NULL
                     CHECK (type IN ('CASH','BANK','CREDIT_CARD','EWALLET','SAVINGS','INVESTMENT')),
    currency         VARCHAR(3) NOT NULL DEFAULT 'PEN',
    -- Saldo al empezar a usar la cuenta. Negativo en tarjetas = deuda.
    initial_balance  NUMERIC(14,2) NOT NULL DEFAULT 0,
    color            VARCHAR(7),
    icon             VARCHAR(40),
    archived         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_user ON accounts(user_id);

-- -----------------------------------------------------
-- Migración de datos existentes: una cuenta por usuario y moneda usada
-- -----------------------------------------------------
INSERT INTO accounts (user_id, name, type, currency, color, icon)
SELECT u.id, 'Efectivo', 'CASH', u.currency_default, '#1D9E75', 'wallet'
FROM users u;

-- Destino para el ahorro de aquí en adelante (transferencias Efectivo → Ahorros)
INSERT INTO accounts (user_id, name, type, currency, color, icon)
SELECT u.id, 'Ahorros', 'SAVINGS', u.currency_default, '#0F6E56', 'piggy-bank'
FROM users u;

INSERT INTO accounts (user_id, name, type, currency)
SELECT DISTINCT t.user_id, 'Efectivo ' || t.currency, 'CASH', t.currency
FROM transactions t
JOIN users u ON u.id = t.user_id
WHERE t.currency <> u.currency_default;

ALTER TABLE transactions
    ADD COLUMN account_id     BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN to_account_id  BIGINT REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN to_amount      NUMERIC(14,2),
    ADD COLUMN exchange_rate  NUMERIC(14,6),
    ADD COLUMN amount_base    NUMERIC(14,2);

UPDATE transactions t
SET account_id = a.id
FROM accounts a
WHERE a.user_id = t.user_id AND a.currency = t.currency;

-- La app nunca permitió elegir moneda al registrar, así que todo lo previo
-- está en la moneda base. Si hubiera filas en otra moneda (creadas vía API),
-- quedan con tipo de cambio 1 y conviene corregirlas a mano.
UPDATE transactions SET exchange_rate = 1, amount_base = amount;

ALTER TABLE transactions
    ALTER COLUMN account_id    SET NOT NULL,
    ALTER COLUMN exchange_rate SET NOT NULL,
    ALTER COLUMN amount_base   SET NOT NULL,
    ALTER COLUMN category_id   DROP NOT NULL;

ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN ('INCOME','EXPENSE','TRANSFER'));

-- Una transferencia tiene destino y no tiene categoría; lo demás, al revés.
ALTER TABLE transactions ADD CONSTRAINT chk_tx_transfer_shape CHECK (
    (type = 'TRANSFER'
        AND category_id IS NULL
        AND to_account_id IS NOT NULL
        AND to_amount IS NOT NULL
        AND to_account_id <> account_id)
    OR
    (type <> 'TRANSFER'
        AND category_id IS NOT NULL
        AND to_account_id IS NULL
        AND to_amount IS NULL)
);
ALTER TABLE transactions ADD CONSTRAINT chk_tx_to_amount_positive CHECK (to_amount IS NULL OR to_amount > 0);
ALTER TABLE transactions ADD CONSTRAINT chk_tx_rate_positive CHECK (exchange_rate > 0);

CREATE INDEX idx_tx_account    ON transactions(account_id);
CREATE INDEX idx_tx_to_account ON transactions(to_account_id) WHERE to_account_id IS NOT NULL;

-- -----------------------------------------------------
-- Semilla para usuarios nuevos: cuentas por defecto.
-- "Ahorro" e "Inversión" dejan de ser categorías de gasto: ahora son
-- transferencias hacia cuentas de tipo SAVINGS / INVESTMENT.
-- -----------------------------------------------------
CREATE OR REPLACE FUNCTION seed_default_data_for_user(p_user_id BIGINT)
RETURNS VOID AS $$
DECLARE
    v_currency VARCHAR(3);
BEGIN
    SELECT currency_default INTO v_currency FROM users WHERE id = p_user_id;

    INSERT INTO accounts (user_id, name, type, currency, color, icon) VALUES
        (p_user_id, 'Efectivo', 'CASH',    v_currency, '#1D9E75', 'wallet'),
        (p_user_id, 'Ahorros',  'SAVINGS', v_currency, '#0F6E56', 'piggy-bank');

    -- Categorías de gasto
    INSERT INTO categories (user_id, name, type, default_bucket, color, icon) VALUES
        (p_user_id, 'Vivienda',         'EXPENSE', 'NEEDS',  '#378ADD', 'home'),
        (p_user_id, 'Alimentación',     'EXPENSE', 'NEEDS',  '#D85A30', 'shopping-cart'),
        (p_user_id, 'Transporte',       'EXPENSE', 'NEEDS',  '#1D9E75', 'car'),
        (p_user_id, 'Salud',            'EXPENSE', 'NEEDS',  '#C84878', 'heart'),
        (p_user_id, 'Servicios',        'EXPENSE', 'NEEDS',  '#888780', 'zap'),
        (p_user_id, 'Educación',        'EXPENSE', 'NEEDS',  '#0C447C', 'book'),
        (p_user_id, 'Entretenimiento',  'EXPENSE', 'WANTS',  '#7F77DD', 'film'),
        (p_user_id, 'Restaurantes',     'EXPENSE', 'WANTS',  '#E89F3E', 'utensils'),
        (p_user_id, 'Ropa',             'EXPENSE', 'WANTS',  '#A86CB8', 'shirt'),
        (p_user_id, 'Suscripciones',    'EXPENSE', 'WANTS',  '#5D8F8B', 'repeat'),
        (p_user_id, 'Pago de deudas',   'EXPENSE', 'DEBT',    '#B23A48', 'credit-card'),
        (p_user_id, 'Otros',            'EXPENSE', 'UNCATEGORIZED', '#6B6B6B', 'more-horizontal');

    -- Categorías de ingreso
    INSERT INTO categories (user_id, name, type, default_bucket, color, icon) VALUES
        (p_user_id, 'Salario',          'INCOME', 'UNCATEGORIZED', '#0F6E56', 'briefcase'),
        (p_user_id, 'Freelance',        'INCOME', 'UNCATEGORIZED', '#2D8F4F', 'laptop'),
        (p_user_id, 'Intereses',        'INCOME', 'UNCATEGORIZED', '#1D9E75', 'percent'),
        (p_user_id, 'Otros ingresos',   'INCOME', 'UNCATEGORIZED', '#5DAB87', 'plus-circle');

    -- Reglas plantilla
    INSERT INTO allocation_rules (user_id, name, description, percentages, is_template) VALUES
        (p_user_id, '50 / 30 / 20',
         'Método Elizabeth Warren. 50% necesidades, 30% deseos, 20% ahorro.',
         '{"NEEDS": 50, "WANTS": 30, "SAVINGS": 20}'::jsonb, TRUE),
        (p_user_id, '70 / 20 / 10',
         'Popular en LATAM. 70% gastos, 20% ahorro, 10% inversión.',
         '{"NEEDS": 70, "SAVINGS": 20, "INVESTMENT": 10}'::jsonb, TRUE),
        (p_user_id, 'Kakebo',
         'Método japonés en cuatro sobres.',
         '{"NEEDS": 50, "WANTS": 20, "SAVINGS": 15, "INVESTMENT": 15}'::jsonb, TRUE);
END;
$$ LANGUAGE plpgsql;
