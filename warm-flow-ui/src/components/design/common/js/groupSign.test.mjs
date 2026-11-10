import test from 'node:test'
import assert from 'node:assert/strict'
import {
  groupSignSummary,
  normalizeGroupSign,
  removeGroupSign,
  serializeGroupSign,
  validateGroupSign
} from './groupSign.js'

test('defaults order to parallel and preserves group array order', () => {
  const sign = normalizeGroupSign({
    groupSign: {
      groups: [
        { id: 'finance', members: ['role:cfo'] },
        { id: 'legal', name: '法务组', members: ['user:alice', 'dept:legal'] }
      ]
    }
  })

  assert.equal(sign.order, 'parallel')
  assert.deepEqual(sign.groups.map(group => group.id), ['finance', 'legal'])
  assert.deepEqual(sign.groups[1].members, ['user:alice', 'dept:legal'])
})

test('normalizes order case to the canonical lower-case value', () => {
  assert.equal(normalizeGroupSign({ groupSign: { order: 'SEQUENCE' } }).order, 'sequence')
  assert.equal(normalizeGroupSign({ groupSign: { order: ' Parallel ' } }).order, 'parallel')
  assert.equal(groupSignSummary({ groupSign: { order: 'SEQUENCE', groups: [{ id: 'a', members: ['u'] }] } })
    , '组签 · 1 组 · 顺序')
})

test('ignores legacy top-level groups and groupOrder', () => {
  const sign = normalizeGroupSign({ groups: ['legacy'], groupOrder: 'sequence' })

  assert.deepEqual(sign.groups, [])
  assert.equal(sign.order, 'parallel')
})

test('serializes groupSign without touching unrelated node config keys', () => {
  const original = {
    variables: { amount: 1 },
    serviceTask: { type: 'class' },
    event: { timerType: 'FIXED' },
    copy: { handlers: ['user:alice'] },
    __extMeta: { legacy: '说明' },
    groups: 'legacy',
    groupOrder: 'legacy'
  }

  const serialized = serializeGroupSign(original, {
    order: 'sequence',
    groups: [{ id: 'legal', name: ' 法务组 ', members: [' user:alice ', 'user:alice', ''] }]
  })

  assert.deepEqual(serialized.variables, original.variables)
  assert.deepEqual(serialized.serviceTask, original.serviceTask)
  assert.deepEqual(serialized.event, original.event)
  assert.deepEqual(serialized.copy, original.copy)
  assert.deepEqual(serialized.__extMeta, original.__extMeta)
  assert.equal(serialized.groups, 'legacy')
  assert.equal(serialized.groupOrder, 'legacy')
  assert.deepEqual(serialized.groupSign, {
    order: 'sequence',
    groups: [{ id: 'legal', name: '法务组', members: ['user:alice', 'user:alice'] }]
  })
  assert.equal(original.groupSign, undefined, '不得原地修改传入的 nodeConfig')
})

test('removes only the groupSign namespace', () => {
  const config = removeGroupSign({ groupSign: { groups: [] }, variables: { a: 1 } })

  assert.equal(config.groupSign, undefined)
  assert.deepEqual(config.variables, { a: 1 })
})

test('builds summary and validates ids and members', () => {
  const nodeConfig = { groupSign: { order: 'sequence', groups: [{ id: 'legal', members: ['user:alice'] }] } }
  assert.equal(groupSignSummary(nodeConfig), '组签 · 1 组 · 顺序')

  const errors = validateGroupSign({
    groups: [
      { id: 'legal', members: [] },
      { id: 'legal', members: ['user:alice'] },
      { id: 'bad id', members: ['user:bob'] }
    ]
  })
  assert.ok(errors.some(message => message.includes('至少选择一名成员')))
  assert.ok(errors.some(message => message.includes('重复')))
  assert.ok(errors.some(message => message.includes('只能包含字母、数字、下划线和中划线')))
})
