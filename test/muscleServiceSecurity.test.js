'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const db = require('../db')
const { calculateSessionMuscleVolume } = require('../services/muscleService')

test('unmapped exercise fallback does not write the exercise name to console', async () => {
  const originalWarn = console.warn
  const calls = []
  console.warn = (...args) => calls.push(args)
  db.configure({ execute: async () => [[]] })
  try {
    await calculateSessionMuscleVolume('opaque-user', '2026-07-19', [{
      name: 'sensitive-custom-exercise-name',
      sets: 1,
      reps: 1,
      weight_kg: 1
    }])
  } finally {
    console.warn = originalWarn
    db.configure({ execute: null })
  }

  assert.equal(calls.length, 0)
})
