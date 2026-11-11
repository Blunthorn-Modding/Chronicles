'use strict'

const assert = require('node:assert/strict')

const patchedParsers = new WeakSet()

function packetId (buffer) {
  return buffer.length === 0 ? 'unknown' : `0x${buffer[0].toString(16)}`
}

function installStrictPacketParser (client, onFailure) {
  const parser = client.deserializer
  if (!parser || patchedParsers.has(parser)) return

  const original = parser.parsePacketBuffer.bind(parser)
  parser.parsePacketBuffer = buffer => {
    try {
      const parsed = original(buffer)
      assert.equal(parsed.metadata.size, buffer.length, 'Unconsumed packet bytes')
      return parsed
    } catch (error) {
      onFailure?.(buffer)
      throw new Error(`Invalid ${client.state} packet ${packetId(buffer)}: ${error.message}`)
    }
  }
  patchedParsers.add(parser)
}

module.exports = { installStrictPacketParser }
