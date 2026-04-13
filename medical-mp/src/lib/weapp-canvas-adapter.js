import './live2dcubismcore.min.js'
import './pixi-shim.js'
import './vendor/cubism4.js'

const createImageWrapper = (canvas) => {
  const image = canvas.createImage()
  image.addEventListener = (type, listener) => {
    if (type === 'load') image.onload = listener
    if (type === 'error') image.onerror = listener
  }
  image.removeEventListener = (type, listener) => {
    if (type === 'load' && image.onload === listener) image.onload = null
    if (type === 'error' && image.onerror === listener) image.onerror = null
  }
  image.removeAttribute = () => {
    image.src = ''
  }
  return image
}

const createDocumentShim = (canvas) => ({
  createElement(type) {
    if (type === 'canvas') {
      return canvas
    }
    if (type === 'img' || type === 'image') {
      return createImageWrapper(canvas)
    }
    return {}
  }
})

let runtimePromise

export const setupWeappCanvasAdapter = async (canvas) => {
  if (!canvas) {
    throw new Error('Canvas node is required for Live2D setup.')
  }

  if (!runtimePromise) {
    const safeSet = (key, value) => {
      try { globalThis[key] = value } catch (e) { /* read-only, skip */ }
    }
    safeSet('window', globalThis)
    safeSet('self', globalThis)
    if (!globalThis.performance) safeSet('performance', { now: () => Date.now() })
    if (!globalThis.HTMLCanvasElement) safeSet('HTMLCanvasElement', function HTMLCanvasElement() {})
    if (!globalThis.HTMLImageElement) safeSet('HTMLImageElement', function HTMLImageElement() {})
    safeSet('document', createDocumentShim(canvas))
    safeSet('requestAnimationFrame', canvas.requestAnimationFrame
      ? canvas.requestAnimationFrame.bind(canvas)
      : (cb) => setTimeout(() => cb(Date.now()), 16))
    safeSet('cancelAnimationFrame', canvas.cancelAnimationFrame
      ? canvas.cancelAnimationFrame.bind(canvas)
      : (id) => clearTimeout(id))

    // 等待 Cubism Core WASM 初始化完成（真机上是异步的）
    const waitForCubismCore = () => {
      if (globalThis.Live2DCubismCore && globalThis.Live2DCubismCore.then) {
        return new Promise((resolve) => globalThis.Live2DCubismCore.then(resolve))
      }
      return Promise.resolve()
    }

    runtimePromise = waitForCubismCore().then(() => globalThis.PIXI.live2d)
  } else {
    try { globalThis.document = createDocumentShim(canvas) } catch (e) { /* read-only */ }
  }

  return runtimePromise
}

export const createCanvasImage = createImageWrapper
