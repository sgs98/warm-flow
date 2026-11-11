import test from 'node:test'
import assert from 'node:assert/strict'
import { isManagedRejectSection, readRejectReturn, writeRejectReturn, rejectReturnGraphErrors } from './rejectReturn.js'
import { json2LogicFlowJson, logicFlowJsonToWarmFlow } from './tool.js'

const DIRECT = 'RETURN_TO_REJECTOR'
const config = { reject: { schemaVersion: 1, afterApproval: DIRECT } }

test('defaults and preserves legacy business reject including objects', () => {
  assert.deepEqual(readRejectReturn({}), { mode: 'REPLAY', conflict: false, error: '' })
  for (const reject of ['legacy', null, [], { business: 1 }]) {
    const legacy = { reject }
    assert.equal(isManagedRejectSection(reject), false)
    assert.equal(readRejectReturn(legacy).conflict, true)
    assert.deepEqual(writeRejectReturn(legacy, 'REPLAY'), legacy)
    assert.throws(() => writeRejectReturn(legacy, DIRECT), /迁移/)
  }
})

test('managed section requires numeric version one and known mode', () => {
  for (const schemaVersion of [1, 1.0, 1e0]) assert.equal(readRejectReturn({ reject: { schemaVersion, afterApproval: DIRECT } }).mode, DIRECT)
  for (const reject of [{ schemaVersion: '1' }, { schemaVersion: 2 }, { schemaVersion: null }, { schemaVersion: 1, afterApproval: 'unknown' }]) {
    assert.equal(isManagedRejectSection(reject), true)
    assert.ok(readRejectReturn({ reject }).error)
    assert.throws(() => writeRejectReturn({ reject }, DIRECT))
  }
  for (const afterApproval of [null, undefined, 'REPLAY']) assert.equal(readRejectReturn({ reject: { schemaVersion: 1, afterApproval } }).mode, 'REPLAY')
})

test('raw nodeConfig near-one versions cannot be rounded into managed version one', () => {
  for (const version of ['1.0000000000000001', '0.99999999999999999', '1.0000000000000001e0', '9.9999999999999999e-1']) {
    const raw = `{"reject":{"schemaVersion":${version},"afterApproval":"${DIRECT}"}}`
    const g = graph(); g.nodes[4].properties.nodeConfig = raw
    assert.ok(rejectReturnGraphErrors(g).some(e => e.message.includes('schemaVersion')), version)
    assert.ok(readRejectReturn(raw).error, version)
  }
  for (const version of ['1', '1.0', '1e0', '1E+0', '10e-1', '0.01e2']) {
    assert.equal(readRejectReturn(`{"reject":{"schemaVersion":${version},"afterApproval":"${DIRECT}"}}`).mode, DIRECT)
  }
})

test('only changes feature fields, preserving nested values and unknown keys', () => {
  const original = { variables: { x: 1 }, copy: { handlers: ['user:a'] }, custom: [null, { x: 1 }] }
  const updated = writeRejectReturn(original, DIRECT)
  assert.deepEqual(updated.reject, config.reject)
  assert.equal(original.reject, undefined)
  assert.deepEqual(writeRejectReturn(updated, 'REPLAY'), original)
  const extended = { ...updated, reject: { ...updated.reject, custom: null } }
  assert.deepEqual(writeRejectReturn(extended, 'REPLAY').reject, { schemaVersion: 1, custom: null })
  assert.deepEqual(updated.variables, original.variables)
  assert.deepEqual(updated.copy, original.copy)
})

test('rejects runtime collisions instead of silently stripping business data', () => {
  for (const runtime of [null, 'legacy', { version: 1, byTaskId: {} }]) {
    assert.throws(() => writeRejectReturn({ __rejectReturnRuntime: runtime }, DIRECT), /运行/)
  }
})

function graph() {
  const nodes = ['start', 'A', 'B', 'C', 'D', 'end'].map(id => ({ id, type: id === 'start' ? 'start' : id === 'end' ? 'end' : 'between', properties: { nodeRatio: '0', ...(id === 'D' ? { nodeConfig: JSON.stringify(config) } : {}) } }))
  const edges = nodes.slice(0, -1).map((n, i) => ({ sourceNodeId: n.id, targetNodeId: nodes[i + 1].id, properties: { skipType: 'PASS' } }))
  edges.push({ sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  return { nodes, edges }
}

test('graph allows linear OR-sign, prioritizes fixed target and rejects unsupported paths', () => {
  assert.deepEqual(rejectReturnGraphErrors(graph()), [])
  const fixed = graph(); fixed.nodes[4].properties.anyNodeSkip = 'B'
  assert.deepEqual(rejectReturnGraphErrors(fixed), [])
  for (const mutation of [g => g.nodes[2].type = 'service', g => g.nodes[2].type = 'parallel', g => g.nodes[2].properties.eventType = 'TIMER_CATCH', g => g.nodes[2].properties.nodeRatio = '100', g => g.nodes[2].properties.cooperateType = 'sequence', g => g.nodes[2].properties.nodeConfig = '{"groupSign":{}}', g => g.edges[1].properties.skipCondition = 'x', g => g.nodes[1].properties.__unused = true]) {
    const g = graph(); mutation(g)
    if (g.nodes[1].properties.__unused) g.nodes.push({ id: 'boundary', type: 'between', properties: { attachedToNodeCode: 'B' } })
    assert.ok(rejectReturnGraphErrors(g).length)
  }
})

test('graph detects imported runtime and invalid managed rules, but allows legacy default mode', () => {
  const g = graph(); g.nodes[1].properties.nodeConfig = '{"__rejectReturnRuntime":{"version":1,"byTaskId":{}}}'
  assert.ok(rejectReturnGraphErrors(g).some(e => e.message.includes('运行')))
  const legacy = graph(); legacy.nodes[4].properties.nodeConfig = '{"reject":{"business":1}}'; legacy.nodes[2].type = 'parallel'
  assert.deepEqual(rejectReturnGraphErrors(legacy), [])
  legacy.nodes[4].properties.nodeConfig = '{"reject":{"schemaVersion":2}}'
  assert.ok(rejectReturnGraphErrors(legacy).length)
})

test('both model conversions and copy preserve static nodeConfig strings', () => {
  for (const modelValue of ['CLASSICS', 'MIMIC']) {
    const definition = { flowCode: 'roundTrip', flowName: 'test', modelValue, nodeList: [{ nodeCode: 'D', nodeName: 'D', nodeType: 1, nodeRatio: '0', nodeConfig: JSON.stringify({ ...config, custom: [null, 'x'] }) }], skipList: [] }
    const canvas = json2LogicFlowJson(JSON.parse(JSON.stringify(definition)))
    const output = JSON.parse(logicFlowJsonToWarmFlow(JSON.parse(JSON.stringify(canvas))))
    assert.deepEqual(JSON.parse(output.nodeList[0].nodeConfig), JSON.parse(definition.nodeList[0].nodeConfig))
  }
})
