'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { installStrictPacketParser } = require('./strict-parser.cjs')

test('strict parser accepts a packet whose codec consumes every byte', () => {
  const parser = { parsePacketBuffer: () => ({ metadata: { size: 2 } }) }
  const client = { deserializer: parser, state: 'play' }
  installStrictPacketParser(client)
  assert.deepEqual(parser.parsePacketBuffer(Buffer.from([0x01, 0x02])), { metadata: { size: 2 } })
})

test('strict parser fails closed when a codec leaves packet bytes unread', () => {
  const parser = { parsePacketBuffer: () => ({ metadata: { size: 1 } }) }
  const client = { deserializer: parser, state: 'play' }
  let failedBuffer
  installStrictPacketParser(client, buffer => { failedBuffer = buffer })

  assert.throws(
    () => parser.parsePacketBuffer(Buffer.from([0x05, 0xaa])),
    /Invalid play packet 0x5: Unconsumed packet bytes/
  )
  assert.deepEqual(failedBuffer, Buffer.from([0x05, 0xaa]))
})

test('strict parser is installed once across protocol state changes', () => {
  let parseCalls = 0
  const parser = { parsePacketBuffer: () => ({ metadata: { size: ++parseCalls } }) }
  const client = { deserializer: parser, state: 'login' }
  installStrictPacketParser(client)
  installStrictPacketParser(client)
  assert.deepEqual(parser.parsePacketBuffer(Buffer.from([0x01])), { metadata: { size: 1 } })
  assert.equal(parseCalls, 1)
})

test('strict parser converts a swallowed PartialReadError into a hard failure', () => {
  const partial = Object.assign(new Error('Truncated packet'), { partialReadError: true })
  const parser = { parsePacketBuffer: () => { throw partial } }
  const client = { deserializer: parser, state: 'play' }
  let failures = 0
  installStrictPacketParser(client, () => { failures++ })
  assert.throws(() => parser.parsePacketBuffer(Buffer.from([0x77])), error => {
    assert.match(error.message, /Invalid play packet 0x77: Truncated packet/)
    assert.equal(error.partialReadError, undefined)
    return true
  })
  assert.equal(failures, 1)
})
