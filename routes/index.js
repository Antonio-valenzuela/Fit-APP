'use strict'

const healthRouter = require('./health')
const musclesRouter = require('./muscles')
const geminiRouter = require('./gemini')
const authRouter = require('./auth')

function registerApiRoutes(app) {
  app.use('/api/health', healthRouter)
  app.use('/api/muscles', musclesRouter)
  app.use('/api/gemini', geminiRouter)
  app.use('/api/auth', authRouter)
}

module.exports = {
  registerApiRoutes,
  healthRouter,
  authRouter,
  musclesRouter,
  geminiRouter
}
