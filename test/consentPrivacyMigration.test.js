'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const migrationDirectory = path.join(__dirname, '..', 'db', 'migrations')
const migrationPath = path.join(migrationDirectory, '006_consent_privacy_hardening.sql')
const identityMigrationPath = path.join(migrationDirectory, '007_identity_and_rate_limit_hardening.sql')
const accountLifecycleMigrationPath = path.join(migrationDirectory, '008_account_lifecycle_hardening.sql')

test('006 sequentially removes consent PII columns and aligns status enums', () => {
  const migrationNames = fs.readdirSync(migrationDirectory)
    .filter(name => /^\d{3}_.+\.sql$/.test(name))
    .sort()

  assert.equal(migrationNames.includes('006_consent_privacy_hardening.sql'), true)
  assert.equal(fs.existsSync(migrationPath), true)

  const sql = fs.readFileSync(migrationPath, 'utf8')
  assert.match(
    sql,
    /ALTER\s+TABLE\s+consent_state[\s\S]*?MODIFY\s+COLUMN\s+status\s+ENUM\s*\(\s*'not_asked'\s*,\s*'granted'\s*,\s*'partial'\s*,\s*'denied'\s*,\s*'revoked'\s*\)/i
  )
  assert.match(
    sql,
    /ALTER\s+TABLE\s+consent_audit[\s\S]*?MODIFY\s+COLUMN\s+action\s+ENUM\s*\(\s*'requested'\s*,\s*'granted'\s*,\s*'partial'\s*,\s*'denied'\s*,\s*'revoked'\s*\)/i
  )
  assert.match(sql, /ALTER\s+TABLE\s+consent_audit[\s\S]*?DROP\s+COLUMN\s+ip_address/i)
  assert.match(sql, /ALTER\s+TABLE\s+consent_audit[\s\S]*?DROP\s+COLUMN\s+user_agent/i)
})

test('007 migrates every protected user key to an exact textual subject and provisions shared quotas', () => {
  assert.equal(fs.existsSync(identityMigrationPath), true)
  const sql = fs.readFileSync(identityMigrationPath, 'utf8')

  for (const table of ['health_records', 'consent_state', 'consent_audit', 'session_muscle_volume']) {
    assert.match(sql, new RegExp(`ALTER\\s+TABLE\\s+${table}[\\s\\S]*?user_id\\s+VARCHAR\\(255\\)`, 'i'))
  }
  assert.equal((sql.match(/user_id\s+VARCHAR\(255\)\s+CHARACTER SET utf8mb4\s+COLLATE utf8mb4_bin/gi) || []).length, 4)
  assert.match(sql, /subject VARCHAR\(255\) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY/i)
  for (const table of ['health_records', 'consent_state', 'consent_audit', 'session_muscle_volume']) {
    assert.match(sql, new RegExp(`UPDATE\\s+${table}\\s+SET\\s+user_id\\s*=\\s*CONCAT\\('legacy:',\\s*user_id\\)`, 'i'))
  }
  assert.match(sql, /CREATE TABLE IF NOT EXISTS auth_principals/i)
  assert.match(sql, /CREATE TABLE IF NOT EXISTS rate_limit_counters/i)
})

test('008 adds durable tombstones and monotonically versioned JWT sessions', () => {
  assert.equal(fs.existsSync(accountLifecycleMigrationPath), true)
  const sql = fs.readFileSync(accountLifecycleMigrationPath, 'utf8')

  assert.match(sql, /ALTER\s+TABLE\s+auth_principals/i)
  assert.match(sql, /ADD\s+COLUMN\s+token_version\s+BIGINT\s+UNSIGNED\s+NOT\s+NULL\s+DEFAULT\s+1/i)
  assert.match(sql, /ADD\s+COLUMN\s+deleted_at\s+DATETIME\(3\)\s+NULL/i)
  assert.match(sql, /ADD\s+INDEX\s+idx_auth_principals_deleted_at\s*\(deleted_at\)/i)
})

test('consent runtime source cannot persist client IP or user agent', () => {
  const routeSource = fs.readFileSync(path.join(__dirname, '..', 'routes', 'health.js'), 'utf8')

  assert.doesNotMatch(routeSource, /ip_address|user_agent|req\.ip|req\.ips|user-agent/i)
})
