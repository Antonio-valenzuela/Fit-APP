'use strict'

const healthRouter = require('./health')
const musclesRouter = require('./muscles')

function registerApiRoutes(app) {
  app.use('/api/health', healthRouter)
  app.use('/api/muscles', musclesRouter)
}

module.exports = {
  registerApiRoutes,
  healthRouter,
  musclesRouter
}
