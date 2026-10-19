const assert = require('node:assert/strict')

function validateCampaignState (state) {
  assert.equal(state.schemaVersion, 1)
  assert.equal(typeof state.sessionNumber, 'number')
  assert.ok(['INACTIVE', 'ACTIVE', 'PAUSED', 'ENDED'].includes(state.phase))
  assert.ok(state.flags && !Array.isArray(state.flags), 'flags must be a boolean map')
  for (const value of Object.values(state.flags)) assert.equal(typeof value, 'boolean')
  assert.ok(Array.isArray(state.completedEvents))
  for (const eventId of state.completedEvents) assert.equal(typeof eventId, 'string')
  assert.ok(Array.isArray(state.history))
  assert.equal(typeof state.nextSequence, 'number')
  for (let index = 0; index < state.history.length; index++) {
    const entry = state.history[index]
    assert.equal(entry.sequence, index, 'history sequence must be contiguous')
    assert.equal(typeof entry.timestamp, 'string')
    assert.equal(typeof entry.action, 'string')
    assert.equal(typeof entry.sessionNumber, 'number')
    assert.equal(typeof entry.target, 'string')
    assert.ok(entry.parameters && !Array.isArray(entry.parameters), 'parameters must be a string map')
    for (const value of Object.values(entry.parameters)) assert.equal(typeof value, 'string')
    assert.equal(typeof entry.actor.name, 'string')
    assert.ok(entry.actor.uuid === null || typeof entry.actor.uuid === 'string')
  }
  assert.equal(state.nextSequence, state.history.length)
  return state
}

module.exports = { validateCampaignState }
