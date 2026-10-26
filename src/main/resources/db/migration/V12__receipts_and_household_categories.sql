-- =====================================================
-- V12: Boletas con detalle y categorías del hogar
-- =====================================================
-- - Una boleta de supermercado mezcla rubros (leche y detergente). La foto se lee con el
--   intérprete, las líneas se agrupan por categoría y se guarda un movimiento por grupo.
--   La boleta queda aparte con el detalle de productos (items) para análisis futuros.
-- - Nuevas categorías por defecto: Limpieza, Cuidado personal y Mascotas. Se agregan
--   también a los usuarios existentes que no las tengan.

CREATE TABLE receipts (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant      VARCHAR(120),
    receipt_date  DATE NOT NULL,
    -- Total pagado, en la moneda de la cuenta
    total         NUMERIC(14,2) NOT NULL CHECK (total > 0),
    currency      VARCHAR(3) NOT NULL,
    account_id    BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    -- Líneas leídas: [{"description":"LECHE GLORIA 400G","amount":4.50,"categoryId":12}, ...]
    items         JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipts_user ON receipts(user_id, receipt_date DESC);

-- Borrar la boleta (deshacer) borra sus movimientos; borrar un movimiento suelto deja la boleta
ALTER TABLE transactions ADD COLUMN receipt_id BIGINT REFERENCES receipts(id) ON DELETE CASCADE;
CREATE INDEX idx_tx_receipt ON transactions(receipt_id) WHERE receipt_id IS NOT NULL;

-- Categorías del hogar para los usuarios que ya existen
INSERT INTO categories (user_id, name, type, default_bucket, color, icon)
SELECT u.id, c.name, 'EXPENSE', 'NEEDS', c.color, c.icon
FROM users u
CROSS JOIN (VALUES
    ('Limpieza',         '#4A8FB5', 'sparkles'),
    ('Cuidado personal', '#C77DBA', 'bath'),
    ('Mascotas',         '#B07A3C', 'paw-print')
) AS c(name, color, icon)
WHERE NOT EXISTS (
    SELECT 1 FROM categories x WHERE x.user_id = u.id AND x.type = 'EXPENSE' AND lower(x.name) = lower(c.name)
);

CREATE OR REPLACE FUNCTION seed_default_data_for_user(p_user_id BIGINT)
RETURNS VOID AS $$
DECLARE
    v_currency VARCHAR(3);
BEGIN
    SELECT currency_default INTO v_currency FROM users WHERE id = p_user_id;

    -- Una sola cuenta para empezar a registrar sin configurar nada
    INSERT INTO accounts (user_id, name, type, currency, color, icon) VALUES
        (p_user_id, 'Principal', 'GENERAL', v_currency, '#1D9E75', 'wallet');

    -- Categorías de gasto
    INSERT INTO categories (user_id, name, type, default_bucket, color, icon) VALUES
        (p_user_id, 'Vivienda',         'EXPENSE', 'NEEDS',  '#378ADD', 'home'),
        (p_user_id, 'Alimentación',     'EXPENSE', 'NEEDS',  '#D85A30', 'shopping-cart'),
        (p_user_id, 'Limpieza',         'EXPENSE', 'NEEDS',  '#4A8FB5', 'sparkles'),
        (p_user_id, 'Cuidado personal', 'EXPENSE', 'NEEDS',  '#C77DBA', 'bath'),
        (p_user_id, 'Mascotas',         'EXPENSE', 'NEEDS',  '#B07A3C', 'paw-print'),
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
