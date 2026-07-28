'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const manifest = require('../package.json')
const lockfile = require('../package-lock.json')

test('pins every direct production dependency to the lockfile version', () => {
  const directDependencies = manifest.dependencies || {}
  assert.equal(Object.keys(directDependencies).length > 0, true)

  for (const [name, declaredVersion] of Object.entries(directDependencies)) {
    assert.match(declaredVersion, /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/, `${name} must use an exact version`)
    assert.equal(
      declaredVersion,
      lockfile.packages[`node_modules/${name}`]?.version,
      `${name} must match package-lock.json`
    )
  }
})

test('overrides the vulnerable body-parser transitive with a patched exact version', () => {
  assert.match(manifest.overrides?.['body-parser'] || '', /^1\.20\.(?:[6-9]|\d{2,})$/)
  assert.notEqual(lockfile.packages['node_modules/body-parser']?.version, '1.20.5')
})
