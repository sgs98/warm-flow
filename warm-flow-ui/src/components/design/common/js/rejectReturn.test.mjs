import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { effectScope, markRaw, nextTick, reactive, ref, watch } from 'vue'
import { isManagedRejectSection, isParallelBranchNode, isParallelReplayInterval, readRejectReturn, writeRejectReturn, rejectReturnGraphErrors } from './rejectReturn.js'
import { getPreviousNodes, json2LogicFlowJson, logicFlowJsonToWarmFlow } from './tool.js'

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
  // 无静态目标（去掉 anyNodeSkip 与退回线）允许保存：目标可由办理人运行时指定，引擎兜底校验
  const dynamic = graph()
  dynamic.nodes[4].properties.anyNodeSkip = undefined
  dynamic.edges = dynamic.edges.filter(e => e.properties.skipType !== 'REJECT')
  assert.deepEqual(rejectReturnGraphErrors(dynamic), [])
  for (const mutation of [g => g.nodes[2].type = 'service', g => g.nodes[2].properties.eventType = 'TIMER_CATCH', g => g.edges[1].properties.skipCondition = 'x', g => g.nodes[1].properties.__unused = true]) {
    const g = graph(); mutation(g)
    if (g.nodes[1].properties.__unused) g.nodes.push({ id: 'boundary', type: 'between', properties: { attachedToNodeCode: 'B' } })
    assert.ok(rejectReturnGraphErrors(g).length)
  }
})

test('rejector inside a parallel branch is treated as replay interval', () => {
  const nodes = [
    { id: 'start', type: 'start', properties: {} },
    { id: 'P', type: 'parallel', properties: {} },
    { id: 'A', type: 'between', properties: {} },
    { id: 'B', type: 'between', properties: { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' } },
    { id: 'X', type: 'between', properties: {} },
    { id: 'end', type: 'end', properties: {} }
  ]
  const edges = [
    { sourceNodeId: 'start', targetNodeId: 'P', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'P', targetNodeId: 'A', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'P', targetNodeId: 'X', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'A', targetNodeId: 'B', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'X', targetNodeId: 'end', properties: { skipType: 'PASS' } },
    { sourceNodeId: 'B', targetNodeId: 'end', properties: { skipType: 'PASS' } }
  ]
  const graph = { nodes, edges }
  // 驳回节点在并行分支内：即使目标在同一分支，也按整体重走，不报路径错误
  assert.equal(isParallelReplayInterval(graph, 'B'), true)
  // 未配置静态目标时同样提示整体重走
  graph.nodes[3].properties.anyNodeSkip = undefined
  assert.equal(isParallelReplayInterval(graph, 'B'), true)
  graph.nodes[3].properties.anyNodeSkip = 'A'
  assert.deepEqual(rejectReturnGraphErrors(graph), [])
})

function nestedGraph(outer = 'parallel', inner = 'inclusive') {
  const gateways = new Map([['outerFork', outer], ['outerJoin', outer], ['innerFork', inner], ['innerJoin', inner]])
  const nodes = ['start', 'outerFork', 'innerFork', 'left', 'right', 'innerJoin', 'A', 'B', 'D', 'sibling', 'outerJoin', 'after', 'end']
    .map(id => ({ id, type: gateways.get(id) || (['start', 'end'].includes(id) ? id : 'between'),
      properties: id === 'D' ? { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' } : {} }))
  const edges = [['start', 'outerFork'], ['outerFork', 'innerFork'], ['outerFork', 'sibling'],
    ['innerFork', 'left'], ['innerFork', 'right'], ['left', 'innerJoin'], ['right', 'innerJoin'],
    ['innerJoin', 'A'], ['A', 'B'], ['B', 'D'], ['D', 'outerJoin'], ['sibling', 'outerJoin'],
    ['outerJoin', 'after'], ['after', 'end']]
    .map(([sourceNodeId, targetNodeId]) => ({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } }))
  edges.push({ sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  return { nodes, edges }
}

test('nested parallel and inclusive branches remain replay-only until the outer join', () => {
  for (const outer of ['parallel', 'inclusive']) {
    for (const inner of ['parallel', 'inclusive']) {
      const graph = nestedGraph(outer, inner)
      for (const code of ['left', 'right', 'sibling', 'A', 'B', 'D']) {
        assert.equal(isParallelReplayInterval(graph, code), true, `${outer}/${inner}/${code}`)
      }
      assert.equal(isParallelReplayInterval(graph, 'after'), false)
      assert.equal(isParallelReplayInterval(graph, 'start'), false)
      assert.deepEqual(rejectReturnGraphErrors(graph), [])
    }
  }
})

test('single-input gateway does not end a containing branch', () => {
  const graph = nestedGraph()
  graph.edges.find(edge => edge.sourceNodeId === 'right').targetNodeId = 'end'
  assert.equal(isParallelReplayInterval(graph, 'D'), true)
})

test('partial joins leave the remaining fork branches open', () => {
  for (const outer of ['parallel', 'inclusive']) {
    for (const join of ['parallel', 'inclusive']) {
      const graph = nestedGraph(outer, join)
      graph.nodes = graph.nodes.filter(node => node.id !== 'innerFork')
      graph.edges = graph.edges.filter(edge => edge.sourceNodeId !== 'innerFork' && edge.targetNodeId !== 'innerFork')
      for (const targetNodeId of ['left', 'right']) {
        graph.edges.push({ sourceNodeId: 'outerFork', targetNodeId, properties: { skipType: 'PASS' } })
      }
      assert.equal(isParallelBranchNode(graph, 'D'), true)
      assert.equal(isParallelBranchNode(graph, 'after'), false)
    }
  }
})

test('exclusive subbranches and cyclic fork entrances do not close an outer fork', () => {
  const exclusive = nestedGraph('parallel', 'inclusive')
  exclusive.nodes.find(node => node.id === 'innerFork').type = 'serial'
  assert.equal(isParallelBranchNode(exclusive, 'D'), true)
  assert.equal(isParallelBranchNode(exclusive, 'after'), false)
  const cycle = nestedGraph('inclusive', 'parallel')
  cycle.edges.push({ sourceNodeId: 'D', targetNodeId: 'innerFork', properties: { skipType: 'PASS' } })
  assert.equal(isParallelBranchNode(cycle, 'D'), true)
  assert.equal(isParallelBranchNode(cycle, 'after'), false)
})

test('complete joins close reopening cycles even without an end path', () => {
  for (const type of ['parallel', 'inclusive']) {
    const nodes = ['start', 'S', 'A', 'F', 'L', 'R', 'J', 'B', 'C', 'D', 'end'].map(id => ({
      id, type: id === 'start' ? 'start' : id === 'end' ? 'end' : id === 'S' ? 'serial'
        : ['F', 'J'].includes(id) ? type : 'between',
      properties: id === 'D' ? { nodeConfig: JSON.stringify(config), anyNodeSkip: 'B' } : {}
    }))
    const edges = [['start', 'S'], ['S', 'A'], ['S', 'end'], ['A', 'F'], ['F', 'L'], ['F', 'R'],
      ['L', 'J'], ['R', 'J'], ['J', 'B'], ['B', 'C'], ['C', 'D'], ['D', 'A']]
      .map(([sourceNodeId, targetNodeId]) => ({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } }))
    const graph = { nodes, edges }
    assert.equal(isParallelBranchNode(graph, 'L'), true)
    assert.equal(isParallelBranchNode(graph, 'D'), false)
    assert.deepEqual(rejectReturnGraphErrors(graph), [])
  }
})

function multiEntryCycleGraph(fork = 'parallel', join = 'inclusive') {
  const nodes = ['start', 'T', 'F', 'L', 'R', 'J', 'A', 'B', 'D', 'S', 'H', 'K', 'end'].map(id => ({
    id, type: id === 'start' ? 'start' : id === 'end' ? 'end' : ['T', 'S'].includes(id) ? 'serial'
      : id === 'F' ? fork : id === 'J' ? join : 'between',
    properties: id === 'D' ? { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' } : {}
  }))
  const edges = [['start', 'T'], ['T', 'F'], ['T', 'end'], ['F', 'L'], ['F', 'R'], ['L', 'J'], ['R', 'J'],
    ['J', 'A'], ['A', 'B'], ['B', 'D'], ['D', 'S'], ['S', 'H'], ['S', 'K'], ['H', 'K'], ['K', 'H']]
    .map(([sourceNodeId, targetNodeId]) => ({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } }))
  edges.push({ sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  return { nodes, edges }
}

test('complete joins before a multi-entry closed cycle still allow direct return', () => {
  for (const fork of ['parallel', 'inclusive']) {
    for (const join of ['parallel', 'inclusive']) {
      const graph = multiEntryCycleGraph(fork, join)
      assert.equal(isParallelBranchNode(graph, 'L'), true)
      assert.equal(isParallelBranchNode(graph, 'R'), true)
      for (const code of ['J', 'A', 'B', 'D', 'S', 'H', 'K']) {
        assert.equal(isParallelBranchNode(graph, code), false, `${fork}/${join}/${code}`)
      }
      assert.equal(isParallelReplayInterval(graph, 'D'), false)
      assert.deepEqual(rejectReturnGraphErrors(graph), [])
    }
  }
})

test('a branch entering a closed cycle before joining keeps the fork open', () => {
  const graph = multiEntryCycleGraph()
  graph.nodes.push({ id: 'Q', type: 'between', properties: {} })
  for (const [sourceNodeId, targetNodeId] of [['F', 'Q'], ['Q', 'J']]) {
    graph.edges.push({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } })
  }
  graph.edges.find(edge => edge.sourceNodeId === 'R').targetNodeId = 'H'
  assert.equal(isParallelBranchNode(graph, 'D'), true)
  assert.deepEqual(rejectReturnGraphErrors(graph), [])
})

test('node after all joins allows direct return even when its return interval crosses gateways', () => {
  const graph = nestedGraph()
  const after = graph.nodes.find(node => node.id === 'after')
  after.properties = { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' }
  assert.equal(isParallelBranchNode(graph, 'after'), false)
  assert.equal(isParallelReplayInterval(graph, 'after'), false)
})

function completedGatewayGraph(first = 'parallel', second = 'inclusive') {
  const types = new Map([['P', first], ['J', first], ['Q', second], ['K', second]])
  const nodes = ['start', 'A', 'P', 'L', 'R', 'J', 'B', 'Q', 'U', 'V', 'K', 'D', 'end'].map(id => ({
    id, type: types.get(id) || (['start', 'end'].includes(id) ? id : 'between'),
    properties: id === 'D' ? { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' } : {}
  }))
  const edges = [['start', 'A'], ['A', 'P'], ['P', 'L'], ['P', 'R'], ['L', 'J'], ['R', 'J'], ['J', 'B'],
    ['B', 'Q'], ['Q', 'U'], ['Q', 'V'], ['U', 'K'], ['V', 'K'], ['K', 'D'], ['D', 'end']]
    .map(([sourceNodeId, targetNodeId]) => ({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } }))
  edges.push({ sourceNodeId: 'D', targetNodeId: 'A', properties: { skipType: 'REJECT' } })
  return { nodes, edges }
}

test('completed parallel and inclusive intervals allow direct return without replay warning', () => {
  for (const first of ['parallel', 'inclusive']) {
    for (const second of ['parallel', 'inclusive']) {
      const graph = completedGatewayGraph(first, second)
      assert.equal(isParallelBranchNode(graph, 'D'), false)
      assert.equal(isParallelReplayInterval(graph, 'D'), false)
      assert.deepEqual(rejectReturnGraphErrors(graph), [])
    }
  }
})

test('completed gateway intervals still reject skipped service event and boundary effects', () => {
  for (const mode of ['service', 'event', 'boundary']) {
    const graph = completedGatewayGraph()
    const skipped = graph.nodes.find(node => node.id === 'U')
    if (mode === 'service') skipped.type = 'service'
    if (mode === 'event') skipped.properties.eventType = 'MESSAGE_CATCH'
    if (mode === 'boundary') graph.nodes.push({ id: 'timer', type: 'between', properties: { attachedToNodeCode: 'U', eventType: 'TIMER_BOUNDARY' } })
    assert.ok(rejectReturnGraphErrors(graph).some(error => error.id === 'D'), mode)
  }
})

test('gateway return validation rejects non-human sources and self targets', () => {
  for (const type of ['end', 'serial', 'parallel', 'inclusive', 'service']) {
    const graph = completedGatewayGraph()
    graph.nodes.find(node => node.id === 'D').type = type
    assert.ok(rejectReturnGraphErrors(graph).some(error => error.id === 'D'), type)
  }
  const graph = completedGatewayGraph()
  graph.nodes.find(node => node.id === 'D').properties.anyNodeSkip = 'D'
  assert.ok(rejectReturnGraphErrors(graph).some(error => error.id === 'D'))
})

test('batch completed-gateway validation avoids repeated full graph reachability searches', () => {
  const count = 64
  const nodes = ['start', 'A', 'F', 'L', 'R', 'J', ...Array.from({ length: count }, (value, index) => `C${index}`), 'end']
    .map(id => ({ id, type: id === 'F' ? 'parallel' : id === 'J' ? 'inclusive'
      : ['start', 'end'].includes(id) ? id : 'between',
      properties: id.startsWith('C') ? { nodeConfig: JSON.stringify(config), anyNodeSkip: 'A' } : {} }))
  const links = [['start', 'A'], ['A', 'F'], ['F', 'L'], ['F', 'R'], ['L', 'J'], ['R', 'J'], ['J', 'C0'],
    ...Array.from({ length: count - 1 }, (value, index) => [`C${index}`, `C${index + 1}`]), [`C${count - 1}`, 'end']]
  let inspections = 0
  const edges = links.map(([sourceNodeId, targetNodeId]) => ({
    get sourceNodeId() { inspections++; return sourceNodeId }, targetNodeId, properties: { skipType: 'PASS' }
  }))
  assert.deepEqual(rejectReturnGraphErrors({ nodes, edges }), [])
  assert.ok(inspections < 100000, `full-edge inspections: ${inspections}`)
})

test('branch legacy direct config permits normal replay with a boundary target', () => {
  const nested = nestedGraph()
  const original = nested.nodes.find(node => node.id === 'D').properties.nodeConfig
  nested.nodes.push({ id: 'boundary', type: 'between', properties: { eventType: 'TIMER_BOUNDARY', attachedToNodeCode: 'A' } })
  nested.edges.push({ sourceNodeId: 'boundary', targetNodeId: 'end', properties: { skipType: 'PASS' } })
  assert.deepEqual(rejectReturnGraphErrors(nested), [])
  assert.equal(nested.nodes.find(node => node.id === 'D').properties.nodeConfig, original)
  const linear = graph()
  linear.nodes.push({ id: 'boundary', type: 'between', properties: { eventType: 'TIMER_BOUNDARY', attachedToNodeCode: 'A' } })
  assert.ok(rejectReturnGraphErrors(linear).length)
})

test('property panel disables direct return only inside a branch and explains replay', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const expression = source.match(/<input type="radio" name="reject-return-mode"[\s\S]*?:disabled="([^"]+)"/)[1]
  const disabledFor = (value, branch, disabled = false) => runInNewContext(expression, {
    disabled, rejectReturnState: { error: '' }, option: { value }, RETURN_TO_REJECTOR: DIRECT,
    parallelBranchNode: branch
  })
  assert.equal(disabledFor(DIRECT, true), true)
  assert.equal(disabledFor('REPLAY', true), false)
  assert.equal(disabledFor(DIRECT, false), false)
  assert.equal(disabledFor('REPLAY', false, true), true)
  assert.match(source, /仅支持按原流程重走/)
})

test('property panel identifies the canvas node by code rather than database id', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const expression = source.match(/const parallelBranchNode = computed\(([\s\S]*?)\);\r?\n/)
  assert.ok(expression, 'branch classification must be available to the property panel')
  const result = runInNewContext(`(${expression[1]})()`, {
    props: { nodes: [], skips: [] }, form: { value: { nodeCode: 'D', id: 'database-row-id' } },
    isParallelBranchNode: (graph, nodeId) => nodeId === 'D'
  })
  assert.equal(result, true)
})

test('pending node rename keeps the stable canvas identity and cannot enable direct return', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const expression = source.match(/const parallelBranchNode = computed\(([\s\S]*?)\);\r?\n/)[1]
  const result = runInNewContext(`(${expression})()`, {
    props: { nodes: [], skips: [], nodeId: 'D' },
    form: { value: { nodeCode: 'D-renamed', id: 'database-row-D' } },
    isParallelBranchNode: (graph, nodeId) => nodeId === 'D'
  })
  assert.equal(result, true)
  const caller = readFileSync(new URL('../vue/propertySetting.vue', import.meta.url), 'utf8')
  assert.match(caller, /v-bind="componentType === between \? \{ nodeId: objId \} : \{\}"/)
})

test('committed rename refreshes the graph snapshot when saving fails and editing continues', () => {
  const caller = readFileSync(new URL('../vue/propertySetting.vue', import.meta.url), 'utf8')
  const body = caller.match(/function commitIdChange \(\) \{([\s\S]*?)\r?\n\}/)[1]
  const sync = caller.match(/function syncPanelGraph\(\) \{[\s\S]*?\r?\n\}/)?.[0] || ''
  const live = nestedGraph()
  const previous = structuredClone(live)
  const objId = { value: 'D' }
  const panelNodes = { value: previous.nodes }, panelSkips = { value: previous.edges }
  const props = { node: { type: 'between' }, nodes: previous.nodes, skips: previous.edges, lf: {
    getNodeModelById: id => live.nodes.find(node => node.id === id),
    changeNodeId: (before, after) => {
      live.nodes.find(node => node.id === before).id = after
      for (const edge of live.edges) {
        if (edge.sourceNodeId === before) edge.sourceNodeId = after
        if (edge.targetNodeId === before) edge.targetNodeId = after
      }
    },
    getGraphData: () => live
  } }
  runInNewContext(`${sync}\n(function() {${body}})()`, {
    props, objId, nodeCode: { value: 'D-renamed' }, panelNodes, panelSkips
  })
  assert.equal(objId.value, 'D-renamed')
  assert.equal(panelNodes.value, live.nodes)
  assert.equal(panelSkips.value, live.edges)
  assert.equal(isParallelBranchNode({ nodes: panelNodes.value, edges: panelSkips.value }, objId.value), true)
  assert.match(caller, /:nodes="panelNodes" :skips="panelSkips"/)
})

function panelGraphHarness(graph) {
  let live = structuredClone(graph), reads = 0
  const listeners = new Map()
  const flow = {
    graphModel: { eventCenter: {
      on: (name, callback) => {
        if (!listeners.has(name)) listeners.set(name, new Set())
        listeners.get(name).add(callback)
      },
      off: (name, callback) => listeners.get(name)?.delete(callback)
    } },
    getGraphData: () => { reads++; return structuredClone(live) },
    getEdgeModelById: id => live.edges.find(edge => edge.id === id),
    setProperties: (id, properties) => Object.assign(live.edges.find(edge => edge.id === id).properties, properties),
    emit: name => { for (const callback of listeners.get(name) || []) callback() }
  }
  const props = reactive({ lf: markRaw(flow), nodes: graph.nodes, skips: graph.edges, disabled: false })
  const source = readFileSync(new URL('../vue/propertySetting.vue', import.meta.url), 'utf8')
  const block = source.slice(source.indexOf('const panelNodes = ref('), source.indexOf('const panelRef = ref('))
  const scope = effectScope()
  const state = scope.run(() => runInNewContext(`${block}\n({ panelNodes, panelSkips, syncPanelGraph })`, {
    props, ref, watch, nextTick
  }))
  return { props, flow, state, scope, replace: graph => { live = structuredClone(graph) }, reads: () => reads,
    listenerCount: () => [...listeners.values()].reduce((total, callbacks) => total + callbacks.size, 0) }
}

function panelTopologyGraph() {
  const graph = nestedGraph()
  graph.edges.forEach((edge, index) => { edge.id = `edge-${index}` })
  graph.edges.find(edge => edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling').properties.skipType = 'REJECT'
  return graph
}

test('property panel batches live topology refresh without changing selection', async () => {
  const graph = panelTopologyGraph(), harness = panelGraphHarness(graph)
  const current = () => ({ nodes: harness.state.panelNodes.value, edges: harness.state.panelSkips.value })
  try {
    assert.equal(isParallelBranchNode(current(), 'D'), false)
    graph.edges.find(edge => edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling').properties.skipType = 'PASS'
    const before = harness.reads()
    harness.replace(graph)
    for (const name of ['edge:add', 'edge:adjust', 'node:properties-change']) harness.flow.emit(name)
    await nextTick()
    assert.equal(isParallelBranchNode(current(), 'D'), true)
    assert.equal(harness.reads(), before + 1)
    graph.edges = graph.edges.filter(edge => !(edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling'))
    harness.replace(graph)
    harness.flow.emit('edge:delete')
    await nextTick()
    assert.equal(isParallelBranchNode(current(), 'D'), false)
  } finally {
    harness.scope.stop()
  }
  assert.equal(harness.listenerCount(), 0)
})

test('changing skip type from the canvas menu refreshes the selected panel immediately', () => {
  const graph = panelTopologyGraph(), harness = panelGraphHarness(graph)
  const parent = readFileSync(new URL('../../../../views/flow-design/index.vue', import.meta.url), 'utf8')
  const setter = parent.match(/function setEdgeSkipType\(edge, skipType\) \{[\s\S]*?\r?\n\}/)[0]
  const edge = graph.edges.find(edge => edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling')
  try {
    runInNewContext(`${setter}\nsetEdgeSkipType(input, 'PASS')`, {
      lf: { value: harness.flow }, input: edge,
      propertySettingRef: { value: { syncPanelGraph: harness.state.syncPanelGraph } },
      proxy: { $modal: { msgSuccess() {} } }
    })
    assert.equal(isParallelBranchNode({ nodes: harness.state.panelNodes.value, edges: harness.state.panelSkips.value }, 'D'), true)
  } finally {
    harness.scope.stop()
  }
})

test('unmount cancels pending graph refreshes and releases canvas subscriptions', async () => {
  const graph = panelTopologyGraph(), harness = panelGraphHarness(graph)
  const before = harness.reads()
  graph.edges.find(edge => edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling').properties.skipType = 'PASS'
  harness.replace(graph)
  harness.flow.emit('edge:add')
  harness.scope.stop()
  await nextTick()
  assert.equal(harness.listenerCount(), 0)
  assert.equal(harness.reads(), before)
})

test('failed leave validation refreshes topology and preserves unfinished form data', async () => {
  const graph = panelTopologyGraph(), harness = panelGraphHarness(graph)
  const source = readFileSync(new URL('../vue/propertySetting.vue', import.meta.url), 'utf8')
  const leave = source.match(/async function confirmLeave \(\) \{[\s\S]*?\r?\n\}/)[0]
  const form = { nodeCode: 'D-renamed', listenerRows: [{ listenerType: 'start', listenerPath: '' }], nodeConfig: config }
  graph.edges.find(edge => edge.sourceNodeId === 'outerFork' && edge.targetNodeId === 'sibling').properties.skipType = 'PASS'
  harness.replace(graph)
  try {
    const result = await runInNewContext(`${leave}\nconfirmLeave()`, {
      props: harness.props, componentType: { value: { name: 'between' } },
      proxy: { $refs: { between: { validate: async () => false } }, $modal: { msgWarning() {} }, $nextTick: callback => callback() },
      syncPanelGraph: harness.state.syncPanelGraph, commitIdChange: () => assert.fail('failed validation must not commit identity'),
      scrollToFirstError() {}, form: { value: form }
    })
    assert.equal(result, false)
    assert.equal(isParallelBranchNode({ nodes: harness.state.panelNodes.value, edges: harness.state.panelSkips.value }, 'D'), true)
    assert.deepEqual(form, { nodeCode: 'D-renamed', listenerRows: [{ listenerType: 'start', listenerPath: '' }], nodeConfig: config })
  } finally {
    harness.scope.stop()
  }
})

test('pending rename preserves reject target choices and unique reject line hints in both modes', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const filtered = source.match(/const filteredNodes = computed\(([\s\S]*?\r?\n\})\);/)[1]
  const hint = source.match(/const rejectReturnHint = computed\(([\s\S]*?\r?\n\})\);/)[1]
  for (const modelValue of ['CLASSICS', 'MIMIC']) {
    const definition = { flowCode: 'stableTargets', modelValue,
      nodeList: ['start', 'A', 'B', 'D', 'end'].map(nodeCode => ({ nodeCode, nodeName: nodeCode,
        nodeType: nodeCode === 'start' ? 0 : nodeCode === 'end' ? 2 : 1, nodeRatio: '0' })),
      skipList: [['start', 'A'], ['A', 'B'], ['B', 'D'], ['D', 'end'], ['D', 'A']].map(([nowNodeCode, nextNodeCode]) => ({
        nowNodeCode, nextNodeCode, skipType: nowNodeCode === 'D' && nextNodeCode === 'A' ? 'REJECT' : 'PASS'
      })) }
    for (const node of definition.nodeList) {
      node.skipList = definition.skipList.filter(skip => skip.nowNodeCode === node.nodeCode)
    }
    const graph = json2LogicFlowJson(definition)
    const context = { props: { nodes: graph.nodes, skips: graph.edges, nodeId: 'D' },
      form: { value: { nodeCode: 'D-renamed', nodeName: 'D', anyNodeSkip: null } }, getPreviousNodes }
    assert.deepEqual([...runInNewContext(`(${filtered})()`, context)].map(node => node.id), ['B', 'A'], modelValue)
    assert.match(runInNewContext(`(${hint})()`, context), /沿退回线退到 A/, modelValue)
  }
})

test('saving many legacy branch configs performs one shared graph analysis', () => {
  const count = 120
  const nodes = [{ id: 'P', type: 'parallel', properties: {} }, { id: 'S', type: 'between', properties: {} },
    { id: 'J', type: 'parallel', properties: {} }, { id: 'end', type: 'end', properties: {} },
    ...Array.from({ length: count }, (value, index) => ({ id: `A${index}`, type: 'between', properties: index
      ? { nodeConfig: JSON.stringify(config), anyNodeSkip: `A${index - 1}` } : {} }))]
  const edges = [['P', 'A0'], ['P', 'S'], ['S', 'J'], [`A${count - 1}`, 'J'], ['J', 'end'],
    ...Array.from({ length: count - 1 }, (value, index) => [`A${index}`, `A${index + 1}`])]
    .map(([sourceNodeId, targetNodeId]) => ({ sourceNodeId, targetNodeId, properties: { skipType: 'PASS' } }))
  const source = readFileSync(new URL('./rejectReturn.js', import.meta.url), 'utf8').replace(/^export /gm, '')
    .replace(/function postDominators\([\s\S]*?\) \{/, match => `${match}\nanalysisCalls++;`)
  const result = runInNewContext(`let analysisCalls = 0;\n${source}\n({ errors: JSON.stringify(rejectReturnGraphErrors(input)), calls: analysisCalls })`, {
    input: { nodes, edges }
  })
  assert.equal(result.errors, '[]')
  assert.equal(result.calls, 1)
})

test('branch panel preserves legacy direct config and rejects attempts to enable it', () => {
  const source = readFileSync(new URL('../vue/between.vue', import.meta.url), 'utf8')
  const body = source.match(/function setRejectReturnMode\(mode\) \{([\s\S]*?)\r?\n\}/)[1]
  const original = { ...config, variables: { amount: 7 }, custom: [null, 'keep'] }
  const form = { value: { nodeConfig: original } }
  const error = { value: '' }
  const context = { form, rejectReturnAttemptError: error, parallelBranchNode: { value: true },
    RETURN_TO_REJECTOR: DIRECT, writeRejectReturn }
  runInNewContext(`(function(mode) {${body}})(RETURN_TO_REJECTOR)`, context)
  assert.equal(form.value.nodeConfig, original)
  assert.match(error.value, /并行.*包容.*重走/)
  runInNewContext(`(function(mode) {${body}})('REPLAY')`, context)
  assert.equal(readRejectReturn(form.value.nodeConfig).mode, 'REPLAY')
  assert.deepEqual(form.value.nodeConfig.variables, original.variables)
  assert.deepEqual(form.value.nodeConfig.custom, original.custom)
})

test('graph allows exclusive gateway with a unique branch to the rejector', () => {
  const unique = graph()
  unique.nodes[2].type = 'serial'
  unique.edges[2].properties.skipCondition = 'gt@@day|3'
  assert.deepEqual(rejectReturnGraphErrors(unique), [])
  // 网关只能作为区间中转，不能作为驳回目标
  const gatewayTarget = graph()
  gatewayTarget.nodes[2].type = 'serial'
  gatewayTarget.nodes[4].properties.anyNodeSkip = 'B'
  assert.ok(rejectReturnGraphErrors(gatewayTarget).length)
  // 两条分支都能到达驳回节点时无法确定返回路径，拒绝
  const ambiguous = graph()
  ambiguous.nodes[2].type = 'serial'
  ambiguous.nodes.push({ id: 'X', type: 'between', properties: {} })
  ambiguous.edges.push({ sourceNodeId: 'B', targetNodeId: 'X', properties: { skipType: 'PASS' } })
  ambiguous.edges.push({ sourceNodeId: 'X', targetNodeId: 'D', properties: { skipType: 'PASS' } })
  assert.ok(rejectReturnGraphErrors(ambiguous).length)
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
