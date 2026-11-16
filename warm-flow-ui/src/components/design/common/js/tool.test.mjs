import test from 'node:test'
import assert from 'node:assert/strict'
import { applyCheckError, applyClassicDesignColor, setCommonStyle } from './tool.js'

const shadowAttributes = ['dx', 'dy', 'stdDeviation', 'flood-color', 'flood-opacity']

function assertSvgShadow(style, rgb, dy, stdDeviation, opacity) {
  assert.equal(typeof style.shadow, 'object', 'LogicFlow shadow 必须是 SVG 属性对象，不能是 CSS 字符串')
  assert.deepEqual(Object.keys({ ...style.shadow }).sort(), [...shadowAttributes].sort())
  assert.deepEqual(style.shadow, {
    dx: 0,
    dy,
    stdDeviation,
    'flood-color': `rgb(${rgb})`,
    'flood-opacity': opacity
  })
}

function setDesignerMode(t, enabled) {
  const original = globalThis.window
  globalThis.window = { __WF_FLOW_DESIGN_MODE__: enabled }
  t.after(() => {
    if (original === undefined) delete globalThis.window
    else globalThis.window = original
  })
}

test('common styles use SVG shadows in both modes and preserve runtime colors', () => {
  const chartStatusColor = ['10,20,30', '40,50,60', '70,80,90']
  for (const type of [undefined, 'mimic']) {
    for (const [status, rgb] of [[undefined, chartStatusColor[2]], [1, chartStatusColor[1]], [2, chartStatusColor[0]]]) {
      const style = setCommonStyle({}, { status, chartStatusColor }, 'node', type)
      assertSvgShadow(style, rgb, 2, 8, 0.12)
      assert.equal(style.stroke, `rgb(${rgb})`)
      assert.equal(style._statusColorRGB, rgb)
      if (status === 1) assert.equal(style.strokeDasharray, '5 4')
    }
  }
  assertSvgShadow(setCommonStyle({}, {}, 'node', 'mimic'), '107,114,128', 2, 8, 0.12)
})

test('validation errors replace the shadow and retain highest color priority', () => {
  const properties = { status: 2, __checkError: '未配置办理人' }
  const style = setCommonStyle({}, properties, 'node', 'mimic')
  assertSvgShadow(style, '245,108,108', 0, 6, 0.35)
  assert.equal(style.stroke, 'rgb(245,108,108)')
  assert.equal(style.strokeWidth, 2)
  assert.equal(style.strokeDasharray, '')
  assert.equal(style._statusColorRGB, '245,108,108')
})

test('classic designer colors use SVG shadows without overriding validation errors', t => {
  setDesignerMode(t, true)
  for (const rgb of ['103,194,58', '64,158,255', '245,108,108', '245,158,11', '19,194,194', '146,84,222']) {
    const style = applyClassicDesignColor(setCommonStyle({}, {}, 'node'), {}, rgb)
    assertSvgShadow(style, rgb, 2, 6, 0.14)
    assert.equal(style.stroke, `rgb(${rgb})`)
  }
  const properties = { __checkError: '校验失败' }
  const errorStyle = applyClassicDesignColor(setCommonStyle({}, properties, 'node'), properties, '64,158,255')
  assertSvgShadow(errorStyle, '245,108,108', 0, 6, 0.35)
  assert.equal(errorStyle.stroke, 'rgb(245,108,108)')
})

test('classic design colors do not change runtime progress styles outside the designer', t => {
  setDesignerMode(t, false)
  const properties = { status: 1, chartStatusColor: ['10,20,30', '40,50,60', '70,80,90'] }
  const style = setCommonStyle({}, properties, 'node')
  const shadow = style.shadow
  assert.equal(applyClassicDesignColor(style, properties, '64,158,255'), style)
  assert.equal(style.shadow, shadow)
  assertSvgShadow(style, '40,50,60', 2, 8, 0.12)
  assert.equal(style.stroke, 'rgb(40,50,60)')
})

test('applying no validation error leaves existing styles unchanged', () => {
  const style = { shadow: { dx: 1 }, stroke: 'original' }
  assert.equal(applyCheckError(style, undefined), style)
  assert.equal(applyCheckError(style, {}), style)
  assert.deepEqual(style, { shadow: { dx: 1 }, stroke: 'original' })
})
