-- Alinea los estados que la API acepta con los ENUM persistidos.
ALTER TABLE consent_state
  MODIFY COLUMN status ENUM(
    'not_asked','granted','partial','denied','revoked'
  ) NOT NULL DEFAULT 'not_asked';

-- Elimina deliberadamente IP y user-agent historicos de la auditoria de consentimiento.
-- La auditoria conserva solo el usuario, plataforma, accion, contexto y fecha necesarios.
ALTER TABLE consent_audit
  MODIFY COLUMN action ENUM(
    'requested','granted','partial','denied','revoked'
  ) NOT NULL,
  DROP COLUMN ip_address,
  DROP COLUMN user_agent;
