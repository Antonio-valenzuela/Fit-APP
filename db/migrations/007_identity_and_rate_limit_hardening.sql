-- JWT `sub` is an opaque textual identifier. Drop the legacy numeric user FKs
-- before changing the protected data boundary to exact string comparison.
SET @health_fk = (
  SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'health_records'
    AND COLUMN_NAME = 'user_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1
);
SET @drop_health_fk = IF(@health_fk IS NULL, 'DO 0', CONCAT('ALTER TABLE health_records DROP FOREIGN KEY `', REPLACE(@health_fk, '`', '``'), '`'));
PREPARE migration_statement FROM @drop_health_fk;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @consent_fk = (
  SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_state'
    AND COLUMN_NAME = 'user_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1
);
SET @drop_consent_fk = IF(@consent_fk IS NULL, 'DO 0', CONCAT('ALTER TABLE consent_state DROP FOREIGN KEY `', REPLACE(@consent_fk, '`', '``'), '`'));
PREPARE migration_statement FROM @drop_consent_fk;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @muscle_fk = (
  SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'session_muscle_volume'
    AND COLUMN_NAME = 'user_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1
);
SET @drop_muscle_fk = IF(@muscle_fk IS NULL, 'DO 0', CONCAT('ALTER TABLE session_muscle_volume DROP FOREIGN KEY `', REPLACE(@muscle_fk, '`', '``'), '`'));
PREPARE migration_statement FROM @drop_muscle_fk;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

ALTER TABLE health_records MODIFY COLUMN user_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE consent_state MODIFY COLUMN user_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE consent_audit MODIFY COLUMN user_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE session_muscle_volume MODIFY COLUMN user_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

UPDATE health_records SET user_id = CONCAT('legacy:', user_id);
UPDATE consent_state SET user_id = CONCAT('legacy:', user_id);
UPDATE consent_audit SET user_id = CONCAT('legacy:', user_id);
UPDATE session_muscle_volume SET user_id = CONCAT('legacy:', user_id);

CREATE TABLE IF NOT EXISTS auth_principals (
  subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO auth_principals (subject) SELECT DISTINCT user_id FROM health_records;
INSERT IGNORE INTO auth_principals (subject) SELECT DISTINCT user_id FROM consent_state;
INSERT IGNORE INTO auth_principals (subject) SELECT DISTINCT user_id FROM consent_audit;
INSERT IGNORE INTO auth_principals (subject) SELECT DISTINCT user_id FROM session_muscle_volume;

ALTER TABLE health_records
  ADD CONSTRAINT fk_health_principal FOREIGN KEY (user_id) REFERENCES auth_principals(subject) ON DELETE CASCADE;
ALTER TABLE consent_state
  ADD CONSTRAINT fk_consent_principal FOREIGN KEY (user_id) REFERENCES auth_principals(subject) ON DELETE CASCADE;
ALTER TABLE consent_audit
  ADD CONSTRAINT fk_consent_audit_principal FOREIGN KEY (user_id) REFERENCES auth_principals(subject) ON DELETE CASCADE;
ALTER TABLE session_muscle_volume
  ADD CONSTRAINT fk_muscle_principal FOREIGN KEY (user_id) REFERENCES auth_principals(subject) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS rate_limit_counters (
  namespace VARCHAR(64) NOT NULL,
  key_hash CHAR(64) NOT NULL,
  total_hits INT UNSIGNED NOT NULL,
  reset_at DATETIME(3) NOT NULL,
  PRIMARY KEY (namespace, key_hash),
  INDEX idx_rate_limit_reset (reset_at)
);
