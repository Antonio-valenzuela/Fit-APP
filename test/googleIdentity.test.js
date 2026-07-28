'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const { createGoogleTokenVerifier } = require('../services/googleIdentity')

test('passes the configured audience to Google verification and returns only sub', async () => {
  const calls = []
  class FakeOAuthClient {
    constructor(clientId) { calls.push({ constructor: clientId }) }
    async verifyIdToken(options) {
      calls.push(options)
      return { getPayload: () => ({ sub: 'opaque-google-sub', email: 'private@example.com' }) }
    }
  }
  const verifier = createGoogleTokenVerifier('web-client.apps.googleusercontent.com', {
    OAuth2ClientClass: FakeOAuthClient
  })

  const identity = await verifier.verify('signed-id-token')

  assert.deepEqual(identity, { subject: 'google:opaque-google-sub' })
  assert.deepEqual(calls[1], {
    idToken: 'signed-id-token',
    audience: 'web-client.apps.googleusercontent.com'
  })
  assert.equal(JSON.stringify(identity).includes('private@example.com'), false)
})
