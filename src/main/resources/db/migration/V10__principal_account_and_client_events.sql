-- =====================================================
-- V10: Cuenta única al empezar y métricas de la app móvil
-- =====================================================
-- - Nuevo tipo de cuenta GENERAL: "todo mi dinero", para quien no quiere separar cuentas.
-- - Los usuarios nuevos arrancan con una sola cuenta "Principal". Pueden crear más
--   (banco, Yape, tarjeta, ahorro) cuando quieran.
-- - client_events: cuánto tarda cada registro en el teléfono y qué errores ve la
--   persona, para medir la marcha blanca desde el backend.

ALTER TABLE accounts DROP CONSTRAINT accounts_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_type_check
    CHECK (type IN ('GENERAL','CASH','BANK','CREDIT_CARD','EWALLET','SAVINGS','INVESTMENT'));

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

-- Eventos que manda la app: ENTRY = un registro guardado (duration_ms = cuánto tardó la
-- persona desde que vio la pantalla hasta tocar Guardar); ERROR = algo falló en el teléfono.
CREATE TABLE client_events (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(10) NOT NULL CHECK (kind IN ('ENTRY','ERROR')),
    duration_ms  INTEGER CHECK (duration_ms IS NULL OR duration_ms >= 0),
    -- ENTRY: desde dónde se abrió el registro (app, notification). ERROR: qué se intentaba (p. ej. save)
    source       VARCHAR(30),
    message      VARCHAR(500),
    platform     VARCHAR(20),
    app_version  VARCHAR(20),
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_events_user ON client_events(user_id, kind, occurred_at);
