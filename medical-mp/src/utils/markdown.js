function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function parseInline(text) {
  let escaped = escapeHtml(text)

  escaped = escaped.replace(/\*\*\*([^\n]+?)\*\*\*/g, '<strong style="font-weight: 700; font-style: italic;">$1</strong>')
  escaped = escaped.replace(/\*\*([^\n]+?)\*\*/g, '<strong style="font-weight: 600;">$1</strong>')
  escaped = escaped.replace(/\*([^\n*][^\n]*?)\*/g, '<em style="font-style: italic;">$1</em>')
  escaped = escaped.replace(/_([^_\n]+)_/g, '<em style="font-style: italic;">$1</em>')
  escaped = escaped.replace(/`([^`]+)`/g, '<code style="display: inline-block; background-color: rgba(15, 23, 42, 0.06); padding: 4rpx 10rpx; border-radius: 8rpx; font-family: monospace; font-size: 0.9em; color: #be185d; margin: 0 4rpx;">$1</code>')

  escaped = escaped.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, label, url) => {
    const normalizedUrl = String(url).trim()
    const safeUrl = normalizedUrl.toLowerCase().startsWith('javascript:') ? '#' : normalizedUrl
    return `<a href="${safeUrl}" style="color: #2563eb; text-decoration: underline; word-break: break-all;">${label}</a>`
  })

  return escaped
}

function isTableDivider(line) {
  return /^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?$/.test(line.trim())
}

function isPipeTableRow(line) {
  const trimmed = line.trim()
  return trimmed.includes('|') && !trimmed.startsWith('>')
}

function parseTableRow(line) {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => parseInline(cell.trim()))
}

function parseTableAlignments(line) {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => {
      const value = cell.trim()
      if (value.startsWith(':') && value.endsWith(':')) return 'center'
      if (value.endsWith(':')) return 'right'
      return 'left'
    })
}

function buildTableHtml(headerCells, bodyRows, alignments) {
  const columnCount = Math.max(headerCells.length, ...bodyRows.map((row) => row.length), 1)
  const width = `${(100 / columnCount).toFixed(4)}%`

  const renderCell = (tag, cell, align, extraStyle = '') => {
    return `<${tag} style="display: inline-block; vertical-align: top; width: ${width}; box-sizing: border-box; padding: 18rpx 16rpx; text-align: ${align}; border-right: 1rpx solid #dbe5f0; border-bottom: 1rpx solid #dbe5f0; word-break: break-word; white-space: normal; ${extraStyle}">${cell || '&nbsp;'}</${tag}>`
  }

  const headerHtml = headerCells
    .map((cell, index) => renderCell('div', cell, alignments[index] || 'left', 'font-weight: 600; color: #1e293b; background-color: #f8fbff;'))
    .join('')

  const bodyHtml = bodyRows
    .map((row, rowIndex) => {
      const rowCells = Array.from({ length: columnCount }, (_, columnIndex) => {
        return renderCell(
          'div',
          row[columnIndex] || '',
          alignments[columnIndex] || 'left',
          `color: #475569; background-color: ${rowIndex % 2 === 0 ? '#ffffff' : '#f8fafc'};`
        )
      }).join('')
      return `<div style="display: block; white-space: nowrap;">${rowCells}</div>`
    })
    .join('')

  return `
    <div style="margin: 20rpx 0; border: 1rpx solid #dbe5f0; border-radius: 16rpx; overflow: hidden; background-color: #ffffff;">
      <div style="overflow-x: auto;">
        <div style="min-width: ${Math.max(columnCount * 220, 520)}rpx; font-size: 27rpx; line-height: 1.6;">
          <div style="display: block; white-space: nowrap;">${headerHtml}</div>
          ${bodyHtml}
        </div>
      </div>
    </div>
  `.replace(/\n\s+/g, '')
}

function buildCodeBlockHtml(codeContent) {
  const normalizedCode = parseInline(codeContent)
  return `<pre style="background-color: #f1f5f9; padding: 24rpx; border-radius: 16rpx; overflow-x: auto; margin: 20rpx 0; border: 1rpx solid #e2e8f0;"><code style="font-family: monospace; font-size: 26rpx; line-height: 1.6; color: #334155; white-space: pre-wrap; word-break: break-all;">${normalizedCode}</code></pre>`
}

export function parseMarkdown(text) {
  if (!text) return ''

  let source = String(text)
  const codeBlocks = []

  source = source.replace(/```([\s\S]*?)```/g, (match, code) => {
    codeBlocks.push(code)
    return `\n___CODEBLOCK_${codeBlocks.length - 1}___\n`
  })

  source = source.replace(/```([\s\S]*)$/, (match, code) => {
    codeBlocks.push(code)
    return `\n___CODEBLOCK_${codeBlocks.length - 1}___\n`
  })

  const lines = source.split(/\r?\n/)
  let html = ''
  let inUl = false
  let inOl = false
  let inP = false
  let inQuote = false

  const closeBlocks = () => {
    let result = ''
    if (inP) { result += '</p>'; inP = false }
    if (inUl) { result += '</ul>'; inUl = false }
    if (inOl) { result += '</ol>'; inOl = false }
    if (inQuote) { result += '</blockquote>'; inQuote = false }
    return result
  }

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index]
    const trimmedLine = line.trim()

    const codeMatch = trimmedLine.match(/^___CODEBLOCK_(\d+)___$/)
    if (codeMatch) {
      html += closeBlocks()
      const codeIndex = Number.parseInt(codeMatch[1], 10)
      let codeContent = codeBlocks[codeIndex] || ''
      codeContent = codeContent.replace(/^[a-z0-9_-]+\r?\n/i, '').replace(/^\r?\n/, '')
      html += buildCodeBlockHtml(codeContent)
      continue
    }

    if (isPipeTableRow(line) && index + 1 < lines.length && isTableDivider(lines[index + 1])) {
      html += closeBlocks()
      const headerCells = parseTableRow(line)
      const alignments = parseTableAlignments(lines[index + 1])
      const bodyRows = []
      index += 2

      while (index < lines.length) {
        const currentLine = lines[index]
        const currentTrimmed = currentLine.trim()
        if (!currentTrimmed || !isPipeTableRow(currentLine) || isTableDivider(currentLine)) {
          index -= 1
          break
        }
        bodyRows.push(parseTableRow(currentLine))
        index += 1
      }

      html += buildTableHtml(headerCells, bodyRows, alignments)
      continue
    }

    const headingMatch = trimmedLine.match(/^(#{1,6})\s+(.*)/)
    const unorderedListMatch = line.match(/^[\*\-]\s+(.*)/)
    const orderedListMatch = line.match(/^\d+\.\s+(.*)/)
    const quoteMatch = line.match(/^>\s*(.*)/)
    const hrMatch = trimmedLine.match(/^(---|___|\*\*\*)$/)

    if (hrMatch) {
      html += closeBlocks()
      html += '<hr style="border: 0; border-top: 2rpx solid #cbd5e1; margin: 32rpx 0;" />'
      continue
    }

    if (headingMatch) {
      html += closeBlocks()
      const level = headingMatch[1].length
      const headingText = parseInline(headingMatch[2])
      const fontSizeMap = {
        1: '44rpx',
        2: '40rpx',
        3: '36rpx',
        4: '32rpx',
        5: '30rpx',
        6: '28rpx'
      }
      const fontSize = fontSizeMap[level] || '32rpx'
      html += `<h${level} style="font-size: ${fontSize}; font-weight: 600; color: #1e293b; margin: 32rpx 0 16rpx; line-height: 1.4;">${headingText}</h${level}>`
      continue
    }

    if (quoteMatch) {
      if (!inQuote) {
        html += closeBlocks()
        html += '<blockquote style="border-left: 6rpx solid #94a3b8; background-color: rgba(241,245,249,0.75); padding: 16rpx 24rpx; margin: 20rpx 0; color: #475569; border-radius: 0 12rpx 12rpx 0;">'
        inQuote = true
      }
      html += `<p style="margin: 0 0 8rpx 0; word-break: break-all; line-height: 1.6; font-style: italic;">${parseInline(quoteMatch[1])}</p>`
      continue
    }

    if (unorderedListMatch) {
      if (inP) { html += '</p>'; inP = false }
      if (inOl) { html += '</ol>'; inOl = false }
      if (inQuote) { html += '</blockquote>'; inQuote = false }
      if (!inUl) {
        html += '<ul style="padding-left: 40rpx; margin: 16rpx 0; list-style-type: disc; color: #334155;">'
        inUl = true
      }
      html += `<li style="margin-bottom: 12rpx; line-height: 1.6; word-break: break-all; display: list-item;">${parseInline(unorderedListMatch[1])}</li>`
      continue
    }

    if (orderedListMatch) {
      if (inP) { html += '</p>'; inP = false }
      if (inUl) { html += '</ul>'; inUl = false }
      if (inQuote) { html += '</blockquote>'; inQuote = false }
      if (!inOl) {
        html += '<ol style="padding-left: 40rpx; margin: 16rpx 0; list-style-type: decimal; color: #334155;">'
        inOl = true
      }
      html += `<li style="margin-bottom: 12rpx; line-height: 1.6; word-break: break-all; display: list-item;">${parseInline(orderedListMatch[1])}</li>`
      continue
    }

    if (!trimmedLine) {
      html += closeBlocks()
      continue
    }

    if (inUl) { html += '</ul>'; inUl = false }
    if (inOl) { html += '</ol>'; inOl = false }
    if (inQuote) { html += '</blockquote>'; inQuote = false }

    if (!inP) {
      html += `<p style="margin: 16rpx 0 0 0; min-height: 1.2em; word-break: break-all; line-height: 1.7; color: #334155;">${parseInline(trimmedLine)}`
      inP = true
    } else {
      html += `<br/>${parseInline(trimmedLine)}`
    }
  }

  html += closeBlocks()
  html = html.replace(/^(<p[^>]*>)<br\/>/, '$1')

  return `<div style="font-size: 30rpx; line-height: 1.7; letter-spacing: 0.4rpx; color: #334155;">${html}</div>`
}
