const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const failures = []

for (const page of app.pages) {
  for (const ext of ['js', 'json', 'wxml', 'wxss']) {
    const file = path.join(root, `${page}.${ext}`)
    if (!fs.existsSync(file)) failures.push(`缺少页面文件：${page}.${ext}`)
  }
}

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(dir, entry.name)
    return entry.isDirectory() ? walk(target) : [target]
  })
}

for (const file of walk(root)) {
  if (file.includes(`${path.sep}node_modules${path.sep}`)) continue
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (file.endsWith('.js')) {
    const source = fs.readFileSync(file, 'utf8')
    try { new Function('require', 'module', 'exports', source) } catch (error) { failures.push(`${relative} JavaScript 语法错误：${error.message}`) }
    if (!relative.startsWith('config/') && /http:\/\/127\.0\.0\.1:8080/.test(source)) failures.push(`${relative} 散落 BASE_URL`)
    if (/\/api\/residents|\/auth\/register|\/api\/users/.test(source)) failures.push(`${relative} 使用禁止的旧接口`)
  }
  if (file.endsWith('.wxml')) {
    const source = fs.readFileSync(file, 'utf8').replace(/<!--[^]*?-->/g, '')
    const stack = []
    const voidTags = new Set(['input', 'image', 'icon', 'progress'])
    for (const match of source.matchAll(/<\/?([\w-]+)(?:\s[^<>]*?)?\s*\/?>/g)) {
      const token = match[0], tag = match[1]
      if (token.startsWith('</')) {
        const open = stack.pop()
        if (open !== tag) { failures.push(`${relative} 标签不匹配：期望 </${open}>，得到 </${tag}>`); break }
      } else if (!token.endsWith('/>') && !voidTags.has(tag)) stack.push(tag)
    }
    if (stack.length) failures.push(`${relative} 存在未闭合标签：${stack.join(', ')}`)
  }
}

if (app.pages.length !== 11) failures.push(`页面数量应为 11，实际 ${app.pages.length}`)
if (failures.length) { console.error(failures.join('\n')); process.exit(1) }
console.log(`PASS：${app.pages.length} 个页面结构完整，JavaScript/WXML 静态检查通过`)
