'use strict'

// database query stub to prevent routes from crashing when database is not running locally.
async function query(sql, params) {
  console.log('[DB Query Stub]', sql, params)
  // Return typical empty row/result structure expected by the health and muscles routers
  return [[]]
}

module.exports = {
  query
}
