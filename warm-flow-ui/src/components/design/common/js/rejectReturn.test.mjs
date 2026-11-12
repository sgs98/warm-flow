import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { isManagedRejectSection, isParallelReplayInterval, readRejectReturn, writeRejectReturn, rejectReturnGraphErrors } from './rejectReturn.js'
import { json2LogicFlowJson, logicFlowJsonToWarmFlow } from './tool.js'

const DIRECT = 'RETURN_TO_REJECTOR'
const config = { reject: { afterApproval: DIRECT } }

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

test('property panel allows collaborative direct return but still rejects event nodes', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const expression = source.match(/const rejectReturnError = computed\(([\s\S]*?)\);\r?\n/)[1]
  const error = (collaborativeWay, event = false) => runInNewContext(`(${expression})()`, {
    rejectReturnState: { value: { mode: DIRECT, error: '' } },
    rejectReturnAttemptError: { value: '' },
    RETURN_TO_REJECTOR: DIRECT,
    isEventNode: { value: event },
    form: { value: { collaborativeWay } }
  })
  for (const mode of ['1', '2', '3', '4', '5', 'group']) assert.equal(error(mode), '')
  assert.match(error('1', true), /事件/)
})

test('managed section requires afterApproval and known mode', () => {
  assert.equal(readRejectReturn({ reject: { afterApproval: DIRECT } }).mode, DIRECT)
  for (const afterApproval of [null, undefined, 'REPLAY']) {
    assert.equal(readRejectReturn({ reject: { afterApproval } }).mode, 'REPLAY')
  }
  for (const reject of [{ afterApproval: 'unknown' }, { afterApproval: 1 }, { afterApproval: {} }]) {
    assert.equal(isManagedRejectSection(reject), true)
    assert.ok(readRejectReturn({ reject }).error)
    assert.throws(() => writeRejectReturn({ reject }, DIRECT))
  }
})

test('only changes feature fields, preserving nested values and unknown keys', () => {
  const original = { variables: { x: 1 }, copy: { handlers: ['user:a'] }, custom: [null, { x: 1 }] }
  const updated = writeRejectReturn(original, DIRECT)
  assert.deepEqual(updated.reject, config.reject)
  assert.equal(original.reject, undefined)
  assert.deepEqual(writeRejectReturn(updated, 'REPLAY'), original)
  const extended = { ...updated, reject: { ...updated.reject, custom: null } }
  assert.deepEqual(writeRejectReturn(extended, 'REPLAY').reject, { custom: null })
  assert.deepEqual(updated.variables, original.variables)
  assert.deepEqual(updated.copy, original.copy)
})

test('legacy reject object without afterApproval is not treated as engine rule', () => {
  assert.equal(readRejectReturn({ reject: { schemaVersion: 1 } }).mode, 'REPLAY')
  assert.equal(readRejectReturn({ reject: { schemaVersion: 1, business: true } }).mode, 'REPLAY')
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
  for (const mutation of [g => g.nodes[2].type = 'service', g => g.nodes[2].type = 'serial', g => g.nodes[2].properties.eventType = 'TIMER_CATCH', g => g.edges[1].properties.skipCondition = 'x', g => g.nodes[1].properties.__unused = true]) {
    const g = graph(); mutation(g)
    if (g.nodes[1].properties.__unused) g.nodes.push({ id: 'boundary', type: 'between', properties: { attachedToNodeCode: 'B' } })
    assert.ok(rejectReturnGraphErrors(g).length)
  }
})

test('graph allows countersign, sequence and group nodes to be skipped', () => {
  for (const props of [{ nodeRatio: '100' }, { nodeRatio: '50' }, { cooperateType: 'sequence' },
    { cooperateType: 'group', nodeConfig: '{"groupSign":{"groups":[]}}' }]) {
    const g = graph()
    Object.assign(g.nodes[2].properties, props)
    assert.deepEqual(rejectReturnGraphErrors(g), [])
  }
})

test('graph allows parallel interval as inert replay config', () => {
  const g = graph()
  g.nodes.splice(2, 1, { id: 'fork', type: 'parallel', properties: {} },
    { id: 'B', type: 'between', properties: { nodeRatio: '0' } },
    { id: 'C', type: 'between', properties: { nodeRatio: '0' } })
  g.edges.length = 0
  g.edges.push(
    { sourceNodeId: 'start', targetNodeId: 'A', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'A', targetNodeId: 'fork', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'fork', targetNodeId: 'B', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'fork', targetNodeId: 'C', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'B', targetNodeId: 'D', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'C', targetNodeId: 'D', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'D', targetNodeId: 'end', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  assert.deepEqual(rejectReturnGraphErrors(g), [])
})

test('parallel replay interval is reported to the designer', () => {
  assert.equal(isParallelReplayInterval(graph(), 'D'), false)
  const g = graph()
  g.nodes.splice(2, 1, { id: 'fork', type: 'parallel', properties: {} },
    { id: 'B', type: 'between', properties: { nodeRatio: '0' } },
    { id: 'C', type: 'between', properties: { nodeRatio: '0' } })
  g.edges.length = 0
  g.edges.push(
    { sourceNodeId: 'start', targetNodeId: 'A', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'A', targetNodeId: 'fork', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'fork', targetNodeId: 'B', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'fork', targetNodeId: 'C', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'B', targetNodeId: 'D', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'C', targetNodeId: 'D', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'D', targetNodeId: 'end', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  assert.equal(isParallelReplayInterval(g, 'D'), true)
})

test('graph allows legacy default mode and rejects invalid managed rules', () => {
  const legacy = graph(); legacy.nodes[4].properties.nodeConfig = '{"reject":{"business":1}}'; legacy.nodes[2].type = 'parallel'
  assert.deepEqual(rejectReturnGraphErrors(legacy), [])
  legacy.nodes[4].properties.nodeConfig = '{"reject":{"afterApproval":"UNKNOWN"}}'
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
