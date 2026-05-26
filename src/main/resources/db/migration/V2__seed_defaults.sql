-- =====================================================
-- V2: Categorías y reglas semilla por defecto
-- =====================================================
-- Función para sembrar datos por defecto cuando se crea un usuario.
-- Se invoca desde el servicio al registrar (no es un trigger para evitar
-- acoplamiento; queda como utilidad reusable).

CREATE OR REPLACE FUNCTION seed_default_data_for_user(p_user_id BIGINT)
RETURNS VOID AS $$
BEGIN
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
        (p_user_id, 'Ahorro',           'EXPENSE', 'SAVINGS', '#0F6E56', 'piggy-bank'),
        (p_user_id, 'Inversión',        'EXPENSE', 'INVESTMENT', '#2D8F4F', 'trending-up'),
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
