-- Registros de salud normalizados (una fila por metrica por dia por usuario)
CREATE TABLE IF NOT EXISTS health_records (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id       INT UNSIGNED NOT NULL,
  date          DATE NOT NULL,
  metric_type   ENUM(
    'steps','heart_rate','sleep','weight',
    'calories_burned','spo2','hrv'
  ) NOT NULL,
  value         DECIMAL(10,3) NOT NULL,
  unit          VARCHAR(20) NOT NULL,
  source        ENUM(
    'android_hc','ios_hk','huawei_hms','manual'
  ) NOT NULL DEFAULT 'manual',
  raw_data      JSON,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_date_metric (user_id, date, metric_type),
  INDEX idx_user_date (user_id, date),
  INDEX idx_metric_type (metric_type),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Estado de consentimiento por plataforma (una fila por usuario+plataforma)
CREATE TABLE IF NOT EXISTS consent_state (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id       INT UNSIGNED NOT NULL,
  platform      ENUM('android_hc','ios_hk','huawei_hms') NOT NULL,
  status        ENUM(
    'not_asked','granted','partial','denied'
  ) NOT NULL DEFAULT 'not_asked',
  denied_types  JSON,
  granted_at    TIMESTAMP NULL,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_platform (user_id, platform),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Auditoria inmutable de cambios de consentimiento
CREATE TABLE IF NOT EXISTS consent_audit (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id       INT UNSIGNED NOT NULL,
  platform      VARCHAR(30) NOT NULL,
  action        ENUM('requested','granted','denied','revoked') NOT NULL,
  context       VARCHAR(100),
  ip_address    VARCHAR(45),
  user_agent    VARCHAR(255),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_created (created_at)
);
