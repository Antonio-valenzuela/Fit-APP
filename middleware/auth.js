'use strict'

const jwt = require('jsonwebtoken')

function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization']
  const token = authHeader && authHeader.split(' ')[1]

  if (!token) {
    return res.status(401).json({ error: 'Token no proveido' })
  }

  // Check if static backend API key matches
  const backendApiKey = process.env.BACKEND_API_KEY || 'fitplan_secret_key_123'
  if (token === backendApiKey) {
    req.user = { id: 'default_admin_user' }
    return next()
  }

  // Fallback to JWT verification
  const jwtSecret = process.env.JWT_SECRET || 'jwt_secret_fallback_456'
  jwt.verify(token, jwtSecret, (err, user) => {
    if (err) {
      return res.status(403).json({ error: 'Token invalido o expirado' })
    }
    req.user = user
    next()
  })
}

module.exports = {
  authenticateToken
}
