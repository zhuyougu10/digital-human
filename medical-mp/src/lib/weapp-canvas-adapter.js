import './live2dcubismcore.min.js'

class MiniEmitter {
  constructor() {
    this._events = Object.create(null)
  }

  on(event, fn, context) {
    const list = this._events[event] || (this._events[event] = [])
    list.push({ fn, context, once: false })
    return this
  }

  once(event, fn, context) {
    const list = this._events[event] || (this._events[event] = [])
    list.push({ fn, context, once: true })
    return this
  }

  off(event, fn, context) {
    if (!event) {
      this._events = Object.create(null)
      return this
    }

    const list = this._events[event]
    if (!list) {
      return this
    }

    this._events[event] = list.filter((entry) => {
      if (fn && entry.fn !== fn) {
        return true
      }

      if (context && entry.context !== context) {
        return true
      }

      return false
    })

    return this
  }

  emit(event, ...args) {
    const list = this._events[event]
    if (!list?.length) {
      return false
    }

    for (const entry of [...list]) {
      entry.fn.apply(entry.context || this, args)
      if (entry.once) {
        this.off(event, entry.fn, entry.context)
      }
    }

    return true
  }

  listeners(event) {
    return (this._events[event] || []).map((entry) => entry.fn)
  }
}

class Matrix {
  constructor(a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0) {
    this.a = a
    this.b = b
    this.c = c
    this.d = d
    this.tx = tx
    this.ty = ty
  }

  set(a, b, c, d, tx, ty) {
    this.a = a
    this.b = b
    this.c = c
    this.d = d
    this.tx = tx
    this.ty = ty
    return this
  }

  identity() {
    return this.set(1, 0, 0, 1, 0, 0)
  }

  copyFrom(matrix) {
    return this.set(matrix.a, matrix.b, matrix.c, matrix.d, matrix.tx, matrix.ty)
  }

  clone() {
    return new Matrix(this.a, this.b, this.c, this.d, this.tx, this.ty)
  }

  translate(x, y) {
    this.tx += x
    this.ty += y
    return this
  }

  scale(x, y) {
    this.a *= x
    this.b *= x
    this.c *= y
    this.d *= y
    this.tx *= x
    this.ty *= y
    return this
  }

  append(matrix) {
    const a1 = this.a
    const b1 = this.b
    const c1 = this.c
    const d1 = this.d
    const tx1 = this.tx
    const ty1 = this.ty

    this.a = a1 * matrix.a + b1 * matrix.c
    this.b = a1 * matrix.b + b1 * matrix.d
    this.c = c1 * matrix.a + d1 * matrix.c
    this.d = c1 * matrix.b + d1 * matrix.d
    this.tx = tx1 * matrix.a + ty1 * matrix.c + matrix.tx
    this.ty = tx1 * matrix.b + ty1 * matrix.d + matrix.ty
    return this
  }

  prepend(matrix) {
    const a1 = this.a
    const b1 = this.b
    const c1 = this.c
    const d1 = this.d
    const tx1 = this.tx
    const ty1 = this.ty

    this.a = matrix.a * a1 + matrix.b * c1
    this.b = matrix.a * b1 + matrix.b * d1
    this.c = matrix.c * a1 + matrix.d * c1
    this.d = matrix.c * b1 + matrix.d * d1
    this.tx = matrix.tx * a1 + matrix.ty * c1 + tx1
    this.ty = matrix.tx * b1 + matrix.ty * d1 + ty1
    return this
  }
}

class Point {
  constructor(x = 0, y = 0) {
    this.x = x
    this.y = y
  }

  clone() {
    return new Point(this.x, this.y)
  }
}

class ObservablePoint extends Point {
  constructor(cb, scope, x = 0, y = 0) {
    super(x, y)
    this.cb = cb
    this.scope = scope
  }

  set(x, y = x) {
    this.x = x
    this.y = y
    this.cb?.call(this.scope)
  }
}

class Transform {
  constructor() {
    this.worldTransform = new Matrix()
  }
}

class Container extends MiniEmitter {
  constructor() {
    super()
    this.transform = new Transform()
    this.worldTransform = this.transform.worldTransform
    this.parent = null
    this.pivot = { set() {} }
  }

  destroy() {}
}

class Texture {}

const createPixiShim = () => ({
  utils: { EventEmitter: MiniEmitter },
  EventEmitter: MiniEmitter,
  Matrix,
  Transform,
  Point,
  ObservablePoint,
  Texture,
  Container
})

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
    // Mini program globalThis.window is read-only, use try/catch for safety
    const safeSet = (key, value) => {
      try { globalThis[key] = value } catch (e) { /* read-only, skip */ }
    }
    safeSet('window', globalThis)
    safeSet('self', globalThis)
    if (!globalThis.performance) safeSet('performance', { now: () => Date.now() })
    if (!globalThis.HTMLCanvasElement) safeSet('HTMLCanvasElement', function HTMLCanvasElement() {})
    if (!globalThis.HTMLImageElement) safeSet('HTMLImageElement', function HTMLImageElement() {})
    if (!globalThis.PIXI) safeSet('PIXI', createPixiShim())
    safeSet('document', createDocumentShim(canvas))
    safeSet('requestAnimationFrame', canvas.requestAnimationFrame
      ? canvas.requestAnimationFrame.bind(canvas)
      : (cb) => setTimeout(() => cb(Date.now()), 16))
    safeSet('cancelAnimationFrame', canvas.cancelAnimationFrame
      ? canvas.cancelAnimationFrame.bind(canvas)
      : (id) => clearTimeout(id))

    runtimePromise = import('./vendor/cubism4.js').then(() => globalThis.PIXI.live2d)
  } else {
    try { globalThis.document = createDocumentShim(canvas) } catch (e) { /* read-only */ }
  }

  return runtimePromise
}

export const createCanvasImage = createImageWrapper
