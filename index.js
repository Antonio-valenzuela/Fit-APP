'use strict'

require('dotenv').config()
const express = require('express')
const cors = require('cors')
const { registerApiRoutes } = require('./routes')

const app = express()
const PORT = process.env.PORT || 3000

app.use(cors())
app.use(express.json({ limit: '10mb' })) // allow larger payloads for vision base64 images

registerApiRoutes(app)

// Basic home route
app.get('/', (req, res) => {
  res.json({ ok: true, message: 'FitPlan MX Backend API Proxy is running.' })
})

// General health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'UP', timestamp: new Date().toISOString() })
})

// Global error handler
app.use((err, req, res, next) => {
  console.error('[Unhandled Error]', err.message)
  res.status(500).json({ error: 'Internal Server Error' })
})

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server listening on port ${PORT} (host: 0.0.0.0)`)
})
