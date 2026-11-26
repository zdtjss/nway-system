/**
 * 提示词富文本编辑器：把 contenteditable 区域的 HTML 还原为 Markdown。
 * 覆盖提示词常用的排版组件（标题、列表、代码块、表格、Mermaid）。
 */

export function htmlToMarkdown(root: HTMLElement): string {
  return normalizeMarkdown(serializeChildren(root))
}

function normalizeMarkdown(text: string): string {
  return text
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function serializeChildren(node: Node): string {
  let result = ''
  node.childNodes.forEach((child) => {
    result += serializeNode(child)
  })
  return result
}

function serializeNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return (node.textContent || '').replace(/\u00a0/g, ' ')
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return ''
  const el = node as HTMLElement
  const tag = el.tagName.toLowerCase()
  const inner = () => serializeChildren(el)
  const wrap = (mark: string) => {
    const text = inner()
    return text.trim() ? `${mark}${text}${mark}` : text
  }
  switch (tag) {
    case 'br':
      return '\n'
    case 'strong':
    case 'b':
      return wrap('**')
    case 'em':
    case 'i':
      return wrap('*')
    case 'del':
    case 's':
      return wrap('~~')
    case 'code':
      return `\`${el.textContent || ''}\``
    case 'a': {
      const href = el.getAttribute('href') || ''
      return href ? `[${inner()}](${href})` : inner()
    }
    case 'img': {
      const src = el.getAttribute('src') || ''
      return src ? `![${el.getAttribute('alt') || ''}](${src})` : ''
    }
    case 'h1':
    case 'h2':
    case 'h3':
    case 'h4':
    case 'h5':
    case 'h6':
      return `${'#'.repeat(Number(tag[1]))} ${inner().trim()}\n\n`
    case 'p':
      return `${inner()}\n\n`
    case 'div': {
      if (el.classList.contains('mermaid-block')) {
        const code = el.querySelector('.mermaid-source')?.textContent || ''
        return `\`\`\`mermaid\n${code.trim()}\n\`\`\`\n\n`
      }
      return `${inner()}\n\n`
    }
    case 'blockquote': {
      const lines = inner().trim().split('\n')
      return lines.map((line) => (line ? `> ${line}` : '>')).join('\n') + '\n\n'
    }
    case 'pre': {
      const codeEl = el.querySelector('code')
      const code = (codeEl?.textContent ?? el.textContent ?? '').replace(/\n$/, '')
      const lang = (codeEl?.className.match(/language-([\w-]+)/) || [])[1] || ''
      return `\`\`\`${lang}\n${code}\n\`\`\`\n\n`
    }
    case 'ul':
    case 'ol':
      return serializeList(el, 0)
    case 'hr':
      return '---\n\n'
    case 'table':
      return serializeTable(el)
    default:
      return inner()
  }
}

function serializeList(list: HTMLElement, depth: number): string {
  const ordered = list.tagName.toLowerCase() === 'ol'
  const indent = '  '.repeat(depth)
  let result = ''
  let index = 1
  Array.from(list.children).forEach((child) => {
    if (child.tagName.toLowerCase() !== 'li') return
    let text = ''
    let nested = ''
    child.childNodes.forEach((node) => {
      if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = (node as HTMLElement).tagName.toLowerCase()
        if (tag === 'ul' || tag === 'ol') {
          nested += serializeList(node as HTMLElement, depth + 1)
          return
        }
      }
      text += serializeNode(node)
    })
    result += `${indent}${ordered ? `${index}.` : '-'} ${text.replace(/\n+/g, ' ').trim()}\n`
    result += nested
    index += 1
  })
  return result + (depth === 0 ? '\n' : '')
}

function serializeTable(table: HTMLElement): string {
  const rows = Array.from(table.querySelectorAll('tr'))
  if (!rows.length) return ''
  const cellText = (cell: Element) => serializeChildren(cell)
    .replace(/\n+/g, ' ')
    .trim()
    .replace(/\|/g, '\\|')
  const header = Array.from(rows[0].children).map(cellText)
  const lines = [
    `| ${header.join(' | ')} |`,
    `| ${header.map(() => '---').join(' | ')} |`
  ]
  rows.slice(1).forEach((row) => {
    lines.push(`| ${Array.from(row.children).map(cellText).join(' | ')} |`)
  })
  return lines.join('\n') + '\n\n'
}
