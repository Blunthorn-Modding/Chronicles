const mineflayer = require('mineflayer')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const fs = require('node:fs')
const path = require('node:path')
const minecraftData = require('minecraft-data')
const minecraftProtocol = require('minecraft-protocol')
const { installStrictPacketParser } = require('./strict-parser.cjs')
const { validateCampaignState } = require('./state-contract.cjs')

const lock = require(path.resolve(__dirname, '..', '..', 'bootstrap.lock.json'))
const minecraftVersion = lock.minecraft
const minecraftProtocolVersion = lock.minecraft_protocol
const statePath = process.env.CHRONICLE_STATE_PATH
const profilesDir = process.env.CHRONICLE_PROFILES_DIR
const phase = process.env.CHRONICLE_SMOKE_PHASE
assert.ok(statePath, 'CHRONICLE_STATE_PATH must point at the isolated smoke world state')
assert.ok(profilesDir, 'CHRONICLE_PROFILES_DIR must point at the isolated smoke world profiles')
assert.ok(['initial', 'restart'].includes(phase), 'CHRONICLE_SMOKE_PHASE must be initial or restart')
assert.equal(typeof minecraftVersion, 'string', 'bootstrap.lock.json must define minecraft')
assert.equal(typeof minecraftProtocolVersion, 'number', 'bootstrap.lock.json must define minecraft_protocol')

const protocolData = minecraftData(minecraftVersion)
assert.equal(protocolData.version.version, minecraftProtocolVersion, 'minecraft-data protocol must match bootstrap.lock.json')
assert.ok(minecraftProtocol.supportedVersions.includes(protocolData.version.minecraftVersion),
  `minecraft-protocol does not support protocol alias ${protocolData.version.minecraftVersion}`)

const bots = []
const failure = Promise.withResolvers()
const deadline = setTimeout(() => { failure.reject(new Error('bot timeout')) }, 150000)

function sleep (milliseconds) { return new Promise(resolve => setTimeout(resolve, milliseconds)) }

function encodeVarInt (value) {
  const bytes = []
  do {
    let next = value & 0x7f
    value >>>= 7
    if (value) next |= 0x80
    bytes.push(next)
  } while (value)
  return Buffer.from(bytes)
}

function readVarInt (buffer, offset) {
  let value = 0
  let shift = 0
  while (offset < buffer.length && shift < 35) {
    const byte = buffer[offset++]
    value |= (byte & 0x7f) << shift
    if (!(byte & 0x80)) return [value, offset]
    shift += 7
  }
  throw new Error('Malformed varint in Chronicle profile payload')
}

function encodeString (value) {
  const encoded = Buffer.from(value, 'utf8')
  return Buffer.concat([encodeVarInt(encoded.length), encoded])
}

function readString (buffer, offset) {
  const [length, start] = readVarInt(buffer, offset)
  const end = start + length
  assert.ok(end <= buffer.length, 'Truncated string in Chronicle profile payload')
  return [buffer.subarray(start, end).toString('utf8'), end]
}

function uuidBytes (uuid) { return Buffer.from(uuid.replaceAll('-', ''), 'hex') }

function botUuid (bot) {
  const uuid = bot._client.uuid
  assert.match(uuid, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    `Mineflayer login UUID is invalid: ${uuid}`)
  return uuid.toLowerCase()
}

function parseView (data) {
  assert.ok(Buffer.isBuffer(data), 'Chronicle profile view must have binary data')
  let offset = 0
  const owner = data.subarray(offset, offset + 16).toString('hex').replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5')
  offset += 16
  const [name, nameOffset] = readString(data, offset); offset = nameOffset
  const [pronouns, pronounsOffset] = readString(data, offset); offset = pronounsOffset
  const [description, descriptionOffset] = readString(data, offset); offset = descriptionOffset
  const revision = data.readBigInt64BE(offset); offset += 8
  const editable = data[offset++] !== 0
  const saved = data[offset++] !== 0
  assert.equal(offset, data.length, 'Unexpected bytes in Chronicle profile view')
  return { owner, name, pronouns, description, revision, editable, saved }
}

function advertiseProfileChannels (bot) {
  bot._client.write('custom_payload', {
    channel: 'minecraft:register',
    data: Buffer.from('chronicle_core:profile_view\0chronicle_core:profile_notice\0', 'utf8')
  })
}

function waitPayload (bot, channel, parser = data => data) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot._client.removeListener('custom_payload', listener)
      reject(new Error(`Timed out waiting for Chronicle payload ${channel}`))
    }, 5000)
    const listener = packet => {
      if (packet.channel !== channel) return
      try {
        const parsed = parser(packet.data)
        clearTimeout(timer)
        bot._client.removeListener('custom_payload', listener)
        resolve(parsed)
      } catch (error) {
        clearTimeout(timer)
        bot._client.removeListener('custom_payload', listener)
        reject(error)
      }
    }
    bot._client.on('custom_payload', listener)
  })
}

function requestProfile (bot, owner) {
  const view = waitPayload(bot, 'chronicle_core:profile_view', parseView)
  bot._client.write('custom_payload', { channel: 'chronicle_core:profile_request', data: uuidBytes(owner) })
  return view
}

function saveProfile (bot, expectedRevision, name, pronouns, description) {
  const view = waitPayload(bot, 'chronicle_core:profile_view', parseView)
  const revision = Buffer.alloc(8)
  revision.writeBigInt64BE(BigInt(expectedRevision))
  bot._client.write('custom_payload', {
    channel: 'chronicle_core:profile_save',
    data: Buffer.concat([revision, encodeString(name), encodeString(pronouns), encodeString(description)])
  })
  return view
}

function expectNotice (bot, expectedKey) {
  const notice = waitPayload(bot, 'chronicle_core:profile_notice', data => readString(data, 0)[0])
  return notice.then(key => {
    assert.equal(key, expectedKey)
    return key
  })
}

function pingServer () {
  return new Promise((resolve, reject) => {
    minecraftProtocol.ping({ host: '127.0.0.1', port: 25575, version: minecraftVersion }, (error, response) => {
      if (error) reject(error)
      else resolve(response)
    })
  })
}

async function join (username) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25575, username, auth: 'offline', version: minecraftVersion })
  bots.push(bot)
  bot.chronicleActionbars = []
  bot.on('actionBar', message => bot.chronicleActionbars.push(message.toString()))
  bot.on('error', error => failure.reject(error))
  bot.on('kicked', reason => failure.reject(new Error('Kicked: ' + JSON.stringify(reason))))
  const strictParsing = () => installStrictPacketParser(bot._client, buffer => {
    if (process.env.CHRONICLE_TRACE_DIR) {
      const packetId = buffer.length === 0 ? 'unknown' : buffer[0].toString(16)
      fs.writeFileSync(path.join(process.env.CHRONICLE_TRACE_DIR, `failed-packet-${bot._client.state}-${packetId}.bin`), buffer)
    }
  })
  strictParsing()
  bot._client.on('state', strictParsing)
  await once(bot, 'spawn')
  advertiseProfileChannels(bot)
  return bot
}

function readState () {
  assert.ok(fs.existsSync(statePath), `campaign state was not saved: ${statePath}`)
  const state = JSON.parse(fs.readFileSync(statePath, 'utf8'))
  return validateCampaignState(state)
}

function stateSnapshot () { return fs.existsSync(statePath) ? fs.readFileSync(statePath, 'utf8') : null }

function nextMessage (bot, text, matcher = () => true) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener('messagestr', listener)
      reject(new Error(`Timed out waiting for command response: ${text}`))
    }, 5000)
    const listener = message => {
      if (!(matcher instanceof RegExp ? matcher.test(message) : matcher(message))) return
      clearTimeout(timer)
      bot.removeListener('messagestr', listener)
      resolve(message)
    }
    bot.on('messagestr', listener)
    bot.chat(text)
  })
}

async function command (bot, text, matcher = () => true) {
  const reply = await Promise.race([nextMessage(bot, text, matcher), failure.promise])
  console.log(`${bot.username} ${text}: ${reply}`)
  return reply
}

async function deniedWithoutMutation (bot, text) {
  const before = stateSnapshot()
  await command(bot, text)
  await sleep(300)
  assert.equal(stateSnapshot(), before, `unauthorized command mutated campaign state: ${text}`)
}

async function queryDaytime (bot) {
  const reply = await command(bot, '/time query daytime', /^The time is \d+$/i)
  return Number(reply.match(/\d+$/)[0])
}

async function assertFrozenAndMobile (bot, expectedStatus) {
  await waitForActionbar(bot, expectedStatus)
  const beforeTime = await queryDaytime(bot)
  const beforePosition = bot.entity.position.clone()
  bot.setControlState('forward', true)
  await sleep(1100)
  bot.setControlState('forward', false)
  const afterTime = await queryDaytime(bot)
  assert.ok(afterTime - beforeTime <= 2, `frozen world advanced from ${beforeTime} to ${afterTime}`)
  assert.ok(bot.entity.position.distanceTo(beforePosition) > 0.1, 'player did not move while world was frozen')
}

async function assertActiveTimeAdvances (bot) {
  const before = await queryDaytime(bot)
  await sleep(1100)
  const after = await queryDaytime(bot)
  assert.ok(after - before >= 10, `active world time did not advance enough: ${before} -> ${after}`)
}

async function taggedPigY (bot) {
  const reply = await command(bot,
    '/data get entity @e[type=minecraft:pig,tag=chronicle_smoke,limit=1,sort=nearest] Pos[1]',
    /following entity data:/i)
  const match = reply.match(/(-?\d+(?:\.\d+)?)d?\.?$/)
  assert.ok(match, `Could not parse tagged pig Y position: ${reply}`)
  return Number(match[1])
}

async function assertPigFrozen (bot) {
  await command(bot, '/summon minecraft:pig ~ ~6 ~ {Tags:["chronicle_smoke"]}', /summoned/i)
  const before = await taggedPigY(bot)
  await sleep(750)
  const after = await taggedPigY(bot)
  assert.ok(Math.abs(after - before) < 0.01, `pig moved while simulation was frozen: ${before} -> ${after}`)
  return after
}

async function assertPigFallsWhenActive (bot, frozenY) {
  try {
    await sleep(900)
    const after = await taggedPigY(bot)
    assert.ok(after < frozenY - 0.1, `pig did not fall after session activation: ${frozenY} -> ${after}`)
  } finally {
    await command(bot, '/kill @e[type=minecraft:pig,tag=chronicle_smoke,limit=1]', /killed/i)
  }
}

function waitForActionbar (bot, matcher) {
  const existing = bot.chronicleActionbars.find(message => matcher.test(message))
  if (existing) return Promise.resolve(existing)
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener('actionBar', listener)
      reject(new Error(`Timed out waiting for Chronicle actionbar: ${matcher}`))
    }, 3000)
    const listener = message => {
      const text = message.toString()
      if (!matcher.test(text)) return
      clearTimeout(timer)
      bot.removeListener('actionBar', listener)
      resolve(text)
    }
    bot.on('actionBar', listener)
  })
}

function profileFile (bot) { return path.join(profilesDir, `${botUuid(bot)}.json`) }

function readProfileFile (bot) {
  const file = profileFile(bot)
  assert.ok(fs.existsSync(file), `profile was not persisted: ${file}`)
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

async function commandProfileCoverage (a) {
  await command(a, '/chronicle profile show', /^Chronicle profile ChronicleTestA: name=ChronicleTestA; pronouns=; description=; revision=0\.$/)
  await command(a, '/chronicle profile set name Rowan Vale', /^Chronicle profile saved; revision=1\.$/)
  await sleep(1050)
  await command(a, '/chronicle profile set pronouns they/them', /^Chronicle profile saved; revision=2\.$/)
  await sleep(1050)
  await command(a, '/chronicle profile set description Keeps the lanterns lit', /^Chronicle profile saved; revision=3\.$/)
  await sleep(1050)
  await command(a, '/chronicle profile clear pronouns', /^Chronicle profile saved; revision=4\.$/)
  await sleep(1050)
  await command(a, '/chronicle profile clear description', /^Chronicle profile saved; revision=5\.$/)
  const profile = readProfileFile(a)
  assert.equal(profile.owner, botUuid(a))
  assert.equal(profile.name, 'Rowan Vale')
  assert.equal(profile.pronouns, '')
  assert.equal(profile.description, '')
  assert.equal(profile.revision, 5)
}

async function packetProfileCoverage (a, b) {
  const other = await requestProfile(b, botUuid(a))
  assert.equal(other.owner, botUuid(a))
  assert.equal(other.name, 'Rowan Vale')
  assert.equal(other.editable, false)
  const beforeOtherSave = fs.readFileSync(profileFile(a), 'utf8')
  const bSaved = await saveProfile(b, 0, 'ChronicleTestB', '', '')
  assert.equal(bSaved.owner, botUuid(b), 'save payload must derive owner from sender')
  assert.equal(bSaved.editable, true)
  assert.equal(fs.readFileSync(profileFile(a), 'utf8'), beforeOtherSave, 'other player save edited viewed profile')
  const beforeBurst = fs.readFileSync(profileFile(b), 'utf8')
  const throttled = expectNotice(b, 'chronicle.profile.error.throttled')
  const revision = Buffer.alloc(8)
  revision.writeBigInt64BE(1n)
  b._client.write('custom_payload', {
    channel: 'chronicle_core:profile_save',
    data: Buffer.concat([revision, encodeString('Burst save'), encodeString(''), encodeString('')])
  })
  await throttled
  assert.equal(fs.readFileSync(profileFile(b), 'utf8'), beforeBurst, 'throttled save mutated profile')
  const own = await requestProfile(a, botUuid(a))
  assert.equal(own.editable, true)
  assert.equal(own.revision, 5n)
  await sleep(1050)
  const beforeStale = fs.readFileSync(profileFile(a), 'utf8')
  const staleNotice = expectNotice(a, 'chronicle.profile.error.stale')
  a._client.write('custom_payload', {
    channel: 'chronicle_core:profile_save',
    data: Buffer.concat([Buffer.alloc(8), encodeString('Wrong revision'), encodeString(''), encodeString('')])
  })
  await staleNotice
  assert.equal(fs.readFileSync(profileFile(a), 'utf8'), beforeStale, 'stale save mutated profile')
  await sleep(1050)
  const saved = await saveProfile(a, 5, 'Rowan Vale', 'they/them', 'Keeps the lanterns lit')
  assert.equal(saved.owner, botUuid(a))
  assert.equal(saved.editable, true)
  assert.equal(saved.saved, true)
  assert.equal(saved.revision, 6n)
  await sleep(1050)
  const beforeInvalid = fs.readFileSync(profileFile(a), 'utf8')
  const invalidNotice = expectNotice(a, 'chronicle.profile.error.invalid')
  a._client.write('custom_payload', {
    channel: 'chronicle_core:profile_save',
    data: Buffer.concat([Buffer.from(saved.revision.toString(16).padStart(16, '0'), 'hex'), encodeString('x'.repeat(49)), encodeString(''), encodeString('')])
  })
  await invalidNotice
  assert.equal(fs.readFileSync(profileFile(a), 'utf8'), beforeInvalid, 'invalid save mutated profile')
}

function assertSession (state, phaseName, sessionNumber) {
  assert.equal(state.phase, phaseName)
  assert.equal(state.sessionNumber, sessionNumber)
}

function assertAuditActorsAreGm (state) {
  for (const entry of state.history) {
    assert.equal(entry.actor.name, 'ChronicleTestA', 'failed/non-op commands must not be audited')
    assert.equal(typeof entry.actor.uuid, 'string', 'player audit actor must include UUID')
  }
}

async function initialPass (a, b) {
  await command(b, '/chronicle status', /Chronicle session status: inactive; number=0\./i)
  await assertFrozenAndMobile(a, /Chronicle \| Session 0: Inactive \| World frozen/i)
  const frozenPigY = await assertPigFrozen(a)
  await commandProfileCoverage(a)
  await command(b, '/chronicle profile show ChronicleTestA', /^Chronicle profile ChronicleTestA: name=Rowan Vale; pronouns=; description=; revision=5\.$/)
  await sleep(250)
  await packetProfileCoverage(a, b)
  await deniedWithoutMutation(a, '/chronicle session resume')
  await deniedWithoutMutation(a, '/chronicle event trigger quiet_below:readiness_check')
  await deniedWithoutMutation(b, '/chronicle session start')
  await command(a, '/chronicle session start 1', /^Started Chronicle session 1\.$/i)
  assertSession(readState(), 'ACTIVE', 1)
  await waitForActionbar(a, /Chronicle \| Session 1: Active/i)
  await assertActiveTimeAdvances(a)
  await assertPigFallsWhenActive(a, frozenPigY)
  await deniedWithoutMutation(b, '/chronicle flag set quiet_below:test_flag true')
  await deniedWithoutMutation(b, '/chronicle event trigger quiet_below:readiness_check')
  await command(a, '/chronicle flag set quiet_below:test_flag true', /flag/i)
  await command(a, '/chronicle flag get quiet_below:test_flag', /Chronicle flag quiet_below:test_flag = true\./i)
  await command(a, '/chronicle flag list', /quiet_below:test_flag/i)
  assert.equal(readState().flags['quiet_below:test_flag'], true)
  const beforeReadOnly = stateSnapshot()
  await command(a, '/chronicle event list', /quiet_below:readiness_check/i)
  await command(a, '/chronicle event preview quiet_below:readiness_check', /quiet_below:readiness_check/i)
  assert.equal(stateSnapshot(), beforeReadOnly, 'event list/preview must not mutate state')
  await command(a, '/chronicle session pause', /^Paused Chronicle session 1\.$/i)
  assertSession(readState(), 'PAUSED', 1)
  await assertFrozenAndMobile(a, /Chronicle \| Session 1: Paused \| World frozen/i)
  await command(a, '/chronicle event trigger quiet_below:readiness_check')
  assert.equal(readState().completedEvents.includes('quiet_below:readiness_check'), false, 'paused event trigger must not complete')
  await command(a, '/chronicle session resume', /^Resumed Chronicle session 1\.$/i)
  await command(a, '/chronicle event trigger quiet_below:readiness_check', /Chronicle event quiet_below:readiness_check completed\./i)
  let state = readState()
  assert.equal(state.flags['quiet_below:readiness_checked'], true)
  assert.ok(state.completedEvents.includes('quiet_below:readiness_check'))
  assert.ok(state.history.some(entry => entry.action === 'event.trigger' && entry.target === 'quiet_below:readiness_check'))
  await command(a, '/chronicle event history 20', /^Chronicle history \(showing /i)
  await command(a, '/chronicle history 20', /^Chronicle history \(showing /i)
  const beforeDuplicate = stateSnapshot()
  await command(a, '/chronicle event trigger quiet_below:readiness_check')
  await sleep(300)
  assert.equal(stateSnapshot(), beforeDuplicate, 'once-only duplicate trigger must not mutate state')
  await command(a, '/chronicle session pause', /^Paused Chronicle session 1\.$/i)
  state = readState()
  assertSession(state, 'PAUSED', 1)
  assertAuditActorsAreGm(state)
}

async function restartPass (a, b) {
  await command(b, '/chronicle status', /Chronicle session status: paused; number=1\./i)
  await assertFrozenAndMobile(a, /Chronicle \| Session 1: Paused \| World frozen/i)
  let state = readState()
  assertSession(state, 'PAUSED', 1)
  assert.equal(state.flags['quiet_below:test_flag'], true)
  assert.equal(state.flags['quiet_below:readiness_checked'], true)
  assert.ok(state.completedEvents.includes('quiet_below:readiness_check'))
  const persisted = await requestProfile(b, botUuid(a))
  assert.deepEqual({ name: persisted.name, pronouns: persisted.pronouns, description: persisted.description, revision: persisted.revision },
    { name: 'Rowan Vale', pronouns: 'they/them', description: 'Keeps the lanterns lit', revision: 6n })
  await deniedWithoutMutation(b, '/chronicle session resume')
  await command(a, '/chronicle session resume', /^Resumed Chronicle session 1\.$/i)
  const beforeDuplicate = stateSnapshot()
  await command(a, '/chronicle event trigger quiet_below:readiness_check')
  await sleep(300)
  assert.equal(stateSnapshot(), beforeDuplicate, 'restart duplicate trigger must not mutate state')
  await command(a, '/chronicle session end', /^Ended Chronicle session 1\.$/i)
  assertSession(readState(), 'ENDED', 1)
  await command(a, '/chronicle session start', /^Started Chronicle session 2\.$/i)
  await command(a, '/chronicle status', /Chronicle session status: active; number=2\./i)
  await command(a, '/chronicle session end', /^Ended Chronicle session 2\.$/i)
  state = readState()
  assertSession(state, 'ENDED', 2)
  await assertFrozenAndMobile(a, /Chronicle \| Session 2: Ended \| World frozen/i)
  assertAuditActorsAreGm(state)
}

async function main () {
  const server = await pingServer()
  assert.equal(server.version.name, minecraftVersion, 'Pinged server version must match bootstrap.lock.json')
  assert.equal(server.version.protocol, minecraftProtocolVersion, 'Pinged server protocol must match bootstrap.lock.json')
  const a = await join('ChronicleTestA')
  const b = await join('ChronicleTestB')
  assert.ok(a.entity.position && b.entity.position)
  await a.waitForChunksToLoad()
  assert.ok(a.blockAt(a.entity.position.offset(0, -1, 0)), 'Expected world block data')
  if (phase === 'initial') await initialPass(a, b)
  else await restartPass(a, b)
  console.log(JSON.stringify({ result: 'PASS', phase, players: bots.map(bot => bot.username) }))
}

Promise.race([main(), failure.promise]).catch(error => {
  console.error(error)
  process.exitCode = 1
}).finally(() => {
  clearTimeout(deadline)
  for (const bot of bots) bot.quit()
})
