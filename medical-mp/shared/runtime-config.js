const DEFAULT_API_BASE = 'http://localhost:8080/api'
const DEFAULT_LIVE2D_BASE = 'http://localhost:8090'

const trimTrailingSlash = (value = '') => value.replace(/\/+$/, '')

const parseUrl = (value) => {
  try {
    return new URL(value)
  } catch (_error) {
    return null
  }
}

const normalizeHostForBrowser = (url, browserHost) => {
  if (!url || !browserHost) return url
  if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
    url.hostname = browserHost
  }
  return url
}

const normalizeAbsoluteBase = (value, browserHost) => {
  const parsed = normalizeHostForBrowser(parseUrl(value), browserHost)
  return parsed ? trimTrailingSlash(parsed.toString()) : trimTrailingSlash(value)
}

export const resolveApiBase = ({ explicitBase, browserHost } = {}) => {
  const base = explicitBase || import.meta.env.VITE_API_BASE || DEFAULT_API_BASE
  return normalizeAbsoluteBase(base, browserHost)
}

export const resolveApiOrigin = (options = {}) => {
  const apiBase = resolveApiBase(options)
  const parsed = parseUrl(apiBase)
  if (parsed) {
    return trimTrailingSlash(parsed.origin)
  }
  return trimTrailingSlash(apiBase.replace(/\/api$/i, ''))
}

export const resolveLive2dBase = ({ explicitBase, apiBase, browserHost } = {}) => {
  const configuredBase = explicitBase || import.meta.env.VITE_LIVE2D_URL || import.meta.env.VITE_LIVE2D_BASE
  if (configuredBase) {
    return normalizeAbsoluteBase(configuredBase, browserHost)
  }

  const parsedApiBase = parseUrl(apiBase || resolveApiBase({ browserHost }))
  if (parsedApiBase) {
    parsedApiBase.port = '8090'
    parsedApiBase.pathname = ''
    parsedApiBase.search = ''
    parsedApiBase.hash = ''
    return trimTrailingSlash(parsedApiBase.toString())
  }

  return normalizeAbsoluteBase(DEFAULT_LIVE2D_BASE, browserHost)
}

export const resolveTtsUrl = (ttsUrl, options = {}) => {
  if (!ttsUrl) return ''
  if (/^https?:\/\//.test(ttsUrl)) return ttsUrl
  const apiBase = resolveApiBase(options)
  const normalizedPath = ttsUrl.startsWith('/') ? ttsUrl : `/${ttsUrl}`
  return `${apiBase}${normalizedPath}`
}

export const getRuntimeConfig = (options = {}) => {
  const apiBase = resolveApiBase(options)
  return {
    apiBase,
    apiOrigin: resolveApiOrigin({ ...options, apiBase }),
    live2dBase: resolveLive2dBase({ ...options, apiBase })
  }
}
