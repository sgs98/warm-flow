import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, utimesSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

function fixture() {
  const root = mkdtempSync(path.join(os.tmpdir(), 'warm-flow-resource-sync-'))
  const ui = path.join(root, 'warm-flow-ui')
  const target = path.join(root, 'warm-flow-plugin/warm-flow-plugin-ui/warm-flow-plugin-vue3-ui/src/main/resources/warm-flow-ui')
  mkdirSync(path.join(ui, 'scripts'), { recursive: true })
  mkdirSync(path.join(ui, 'src'), { recursive: true })
  mkdirSync(path.join(ui, 'dist/js'), { recursive: true })
  mkdirSync(target, { recursive: true })
  cpSync(new URL('./sync-plugin-resources.mjs', import.meta.url), path.join(ui, 'scripts/sync-plugin-resources.mjs'))
  writeFileSync(path.join(ui, 'src/app.js'), 'export const revision = "current"\n')
  writeFileSync(path.join(ui, 'dist/index.html'), '<script src="./js/app.js"></script>\n')
  writeFileSync(path.join(ui, 'dist/js/app.js'), 'current build\n')
  writeFileSync(path.join(target, 'index.html'), 'previous working build\n')
  const builtAt = new Date(Date.now() - 10000)
  const sourceAt = new Date(builtAt.getTime() - 10000)
  utimesSync(path.join(ui, 'src/app.js'), sourceAt, sourceAt)
  utimesSync(path.join(ui, 'dist/index.html'), builtAt, builtAt)
  return { root, ui, target, builtAt,
    run: () => execFileSync(process.execPath, [path.join(ui, 'scripts/sync-plugin-resources.mjs')], {
      encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe']
    }) }
}

test('fresh production resources can be synchronized', () => {
  const sample = fixture()
  try {
    sample.run()
    assert.equal(readFileSync(path.join(sample.target, 'js/app.js'), 'utf8'), 'current build\n')
    assert.equal(readFileSync(path.join(sample.target, 'index.html'), 'utf8'), '<script src="./js/app.js"></script>\n')
  } finally {
    rmSync(sample.root, { recursive: true, force: true })
  }
})

test('stale source and build inputs cannot overwrite existing plugin resources', () => {
  for (const input of ['src/components/panel.vue', 'vite/plugins/index.js', 'vite.config.js', 'public/logo.svg', '.env.production', 'package.json', 'yarn.lock']) {
    const sample = fixture()
    try {
      const inputPath = path.join(sample.ui, input)
      mkdirSync(path.dirname(inputPath), { recursive: true })
      writeFileSync(inputPath, 'changed build input\n')
      const changedAt = new Date(sample.builtAt.getTime() + 1000)
      utimesSync(inputPath, changedAt, changedAt)
      assert.throws(sample.run, error => /产物已过期/.test(error.stderr.toString()) && error.stderr.toString().includes(input), input)
      assert.equal(readFileSync(path.join(sample.target, 'index.html'), 'utf8'), 'previous working build\n')
      assert.equal(existsSync(path.join(sample.target, 'js/app.js')), false)
      assert.equal(existsSync(`${sample.target}.tmp`), false)
    } finally {
      rmSync(sample.root, { recursive: true, force: true })
    }
  }
})

test('missing referenced asset fails before changing the plugin directory', () => {
  const sample = fixture()
  try {
    rmSync(path.join(sample.ui, 'dist/js/app.js'))
    assert.throws(sample.run, error => /引用的资源不存在/.test(error.stderr.toString()))
    assert.equal(readFileSync(path.join(sample.target, 'index.html'), 'utf8'), 'previous working build\n')
  } finally {
    rmSync(sample.root, { recursive: true, force: true })
  }
})
