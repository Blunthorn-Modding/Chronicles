'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { validateCampaignState } = require('./state-contract.cjs')

function validState () {
  return {
    schemaVersion: 1, sessionNumber: 1, phase: 'PAUSED', flags: { clue: true },
    completedEvents: ['quiet_below:readiness_check'],
    history: [{ sequence: 0, timestamp: '2026-10-18T00:00:00Z', actor: { name: 'GM', uuid: 'uuid' }, action: 'event.trigger', sessionNumber: 1, target: 'quiet_below:readiness_check', parameters: {} }],
    nextSequence: 1
  }
}

test('campaign state validator accepts the M1 persistence contract', () => {
  assert.equal(validateCampaignState(validState()).phase, 'PAUSED')
})

test('campaign state validator rejects non-contiguous history sequences', () => {
  const state = validState()
  state.history[0].sequence = 1
  assert.throws(() => validateCampaignState(state), /history sequence/)
})
