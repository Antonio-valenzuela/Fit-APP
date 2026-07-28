'use strict'

function createGoogleTokenVerifier(clientId, { OAuth2ClientClass } = {}) {
  if (typeof clientId !== 'string' || !clientId.trim()) {
    throw new TypeError('Google OAuth client ID is required')
  }
  const Client = OAuth2ClientClass || require('google-auth-library').OAuth2Client
  const client = new Client(clientId)

  return Object.freeze({
    async verify(idToken) {
      const ticket = await client.verifyIdToken({
        idToken,
        audience: clientId
      })
      const payload = ticket.getPayload()
      if (typeof payload?.sub !== 'string' || !payload.sub.trim() || payload.sub !== payload.sub.trim() || payload.sub.length > 248) {
        throw new Error('Invalid Google identity subject')
      }
      return Object.freeze({ subject: `google:${payload.sub}` })
    }
  })
}

module.exports = { createGoogleTokenVerifier }
