-- =====================================================
-- V9: Sesiones largas para la app móvil (refresh tokens)
-- =====================================================

-- Solo se guarda el hash SHA-256 del token: una filtración de la base no permite usarlos.
-- Cada uso lo rota (se revoca y se emite otro); reusar uno revocado revoca toda la familia.
CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;
