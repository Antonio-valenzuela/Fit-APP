'use strict'

const { createHash } = require('node:crypto')

function rowsFrom(result) {
  return Array.isArray(result) && Array.isArray(result[0]) ? result[0] : result
}

class MySqlRateLimitStore {
  constructor({ database, namespace, now = Date.now }) {
    if (!database?.transaction) throw new TypeError('database with transactions is required')
    if (typeof namespace !== 'string' || !namespace.trim()) throw new TypeError('namespace is required')
    this.database = database
    this.namespace = namespace.trim()
    this.now = now
    this.windowMs = 60_000
    this.nextCleanupAt = 0
  }

  init(options) {
    this.windowMs = options.windowMs
  }

  hashKey(key) {
    return createHash('sha256').update(String(key)).digest('hex')
  }

  async increment(key) {
    const keyHash = this.hashKey(key)
    const cleanupDue = this.now() >= this.nextCleanupAt
    if (cleanupDue) this.nextCleanupAt = this.now() + this.windowMs
    return this.database.transaction(async tx => {
      if (cleanupDue) {
        await tx.query(
          `DELETE FROM rate_limit_counters
           WHERE namespace = ? AND reset_at <= CURRENT_TIMESTAMP(3)
           LIMIT 500`,
          [this.namespace]
        )
      }
      await tx.query(
        `INSERT INTO rate_limit_counters
           (namespace, key_hash, total_hits, reset_at)
         VALUES (?, ?, 1, TIMESTAMPADD(MICROSECOND, ?, CURRENT_TIMESTAMP(3)))
         ON DUPLICATE KEY UPDATE
           total_hits = IF(reset_at <= CURRENT_TIMESTAMP(3), 1, total_hits + 1),
           reset_at = IF(reset_at <= CURRENT_TIMESTAMP(3), VALUES(reset_at), reset_at)`,
        [this.namespace, keyHash, this.windowMs * 1000]
      )
      const rows = rowsFrom(await tx.query(
        `SELECT total_hits,
                CAST(UNIX_TIMESTAMP(reset_at) * 1000 AS UNSIGNED) AS reset_at_ms
         FROM rate_limit_counters
         WHERE namespace = ? AND key_hash = ?`,
        [this.namespace, keyHash]
      ))
      const row = rows[0]
      if (!row) throw new Error('Rate limit counter unavailable')
      return { totalHits: Number(row.total_hits), resetTime: new Date(Number(row.reset_at_ms)) }
    })
  }

  async decrement(key) {
    await this.database.transaction(tx => tx.query(
      `UPDATE rate_limit_counters
       SET total_hits = GREATEST(total_hits - 1, 0)
       WHERE namespace = ? AND key_hash = ?`,
      [this.namespace, this.hashKey(key)]
    ))
  }

  async resetKey(key) {
    await this.database.transaction(tx => tx.query(
      'DELETE FROM rate_limit_counters WHERE namespace = ? AND key_hash = ?',
      [this.namespace, this.hashKey(key)]
    ))
  }

  async resetAll() {
    await this.database.transaction(tx => tx.query(
      'DELETE FROM rate_limit_counters WHERE namespace = ?',
      [this.namespace]
    ))
  }
}

module.exports = { MySqlRateLimitStore }
