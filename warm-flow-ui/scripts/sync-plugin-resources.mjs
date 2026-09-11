import { cpSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const uiRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const distDir = path.join(uiRoot, 'dist')
const targetDir = path.join(
  uiRoot,
  '..',
  'warm-flow-plugin',
  'warm-flow-plugin-ui',
  'warm-flow-plugin-vue3-ui',
  'src',
  'main',
  'resources',
  'warm-flow-ui'
)
const indexPath = path.join(distDir, 'index.html')

if (!existsSync(indexPath)) {
  throw new Error('请先执行 npm run build:prod 生成 warm-flow-ui/dist/index.html')
}

const html = readFileSync(indexPath, 'utf8')
const references = [...html.matchAll(/(?:href|src)="([^"]+)"/g)]
  .map((match) => match[1])
  .filter((reference) => !/^(?:https?:)?\/\//.test(reference) && !reference.startsWith('data:'))

for (const reference of references) {
  const resourcePath = path.join(distDir, reference.replace(/^\.\//, ''))
  if (!existsSync(resourcePath)) {
    throw new Error(`index.html 引用的资源不存在：${reference}`)
  }
}

const tempDir = `${targetDir}.tmp`
rmSync(tempDir, { recursive: true, force: true })
mkdirSync(tempDir, { recursive: true })
cpSync(distDir, tempDir, { recursive: true })
rmSync(targetDir, { recursive: true, force: true })
renameSync(tempDir, targetDir)
