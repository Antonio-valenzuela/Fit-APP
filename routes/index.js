'use strict'

const healthRouter = require('./health')
const musclesRouter = require('./muscles')
const geminiRouter = require('./gemini')

function registerApiRoutes(app) {
  app.use('/api/health', healthRouter)
  app.use('/api/muscles', musclesRouter)
  app.use('/api/gemini', geminiRouter)
}

module.exports = {
  registerApiRoutes,
  healthRouter,
  musclesRouter,
  geminiRouter
}
