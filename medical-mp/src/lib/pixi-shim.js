// PIXI shim - 必须在 cubism4.js 之前加载
// 提供 cubism4.js IIFE 执行时需要的所有父类和工具

// atob/btoa polyfill — 真机小程序没有这两个全局函数
// 用 wx.arrayBufferToBase64 / wx.base64ToArrayBuffer 实现
if (typeof atob === 'undefined') {
  globalThis.atob = (b64) => {
    // wx.base64ToArrayBuffer 返回 ArrayBuffer
    if (typeof wx !== 'undefined' && wx.base64ToArrayBuffer) {
      const buf = wx.base64ToArrayBuffer(b64)
      const bytes = new Uint8Array(buf)
      let result = ''
      for (let i = 0; i < bytes.length; i++) {
        result += String.fromCharCode(bytes[i])
      }
      return result
    }
    // 纯 JS fallback
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
    const str = String(b64).replace(/=+$/, '')
    let output = ''
    for (let i = 0; i < str.length; ) {
      const a = chars.indexOf(str.charAt(i++)) & 0x3F
      const b = chars.indexOf(str.charAt(i++)) & 0x3F
      const c = chars.indexOf(str.charAt(i++)) & 0x3F
      const d = chars.indexOf(str.charAt(i++)) & 0x3F
      output += String.fromCharCode((a << 2) | (b >> 4))
      if (c !== 64) output += String.fromCharCode(((b & 15) << 4) | (c >> 2))
      if (d !== 64) output += String.fromCharCode(((c & 3) << 6) | d)
    }
    return output
  }
  globalThis.btoa = (str) => {
    if (typeof wx !== 'undefined' && wx.arrayBufferToBase64) {
      const bytes = new Uint8Array(str.length)
      for (let i = 0; i < str.length; i++) bytes[i] = str.charCodeAt(i)
      return wx.arrayBufferToBase64(bytes.buffer)
    }
    return ''
  }
}

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
    if (!event) { this._events = Object.create(null); return this }
    const list = this._events[event]
    if (!list) return this
    this._events[event] = list.filter(
      (h) => !(fn && h.fn === fn) || !(context && h.context === context)
    )
    return this
  }
  emit(event, ...args) {
    const list = this._events[event]
    if (!list?.length) return false
    for (const handler of [...list]) {
      handler.fn.apply(handler.context || this, args)
      if (handler.once) this.off(event, handler.fn, handler.context)
    }
    return true
  }
  listeners(event) {
    return (this._events[event] || []).map((h) => h.fn)
  }
}

class Matrix {
  constructor(a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0) {
    this.a = a; this.b = b; this.c = c; this.d = d; this.tx = tx; this.ty = ty
  }
  set(a, b, c, d, tx, ty) { this.a=a; this.b=b; this.c=c; this.d=d; this.tx=tx; this.ty=ty; return this }
  identity() { return this.set(1, 0, 0, 1, 0, 0) }
  copyFrom(m) { return this.set(m.a, m.b, m.c, m.d, m.tx, m.ty) }
  clone() { return new Matrix(this.a, this.b, this.c, this.d, this.tx, this.ty) }
  translate(x, y) { this.tx += x; this.ty += y; return this }
  scale(x, y) { this.a*=x; this.b*=x; this.c*=y; this.d*=y; this.tx*=x; this.ty*=y; return this }
  append(m) {
    const a=this.a, b=this.b, c=this.c, d=this.d, tx=this.tx, ty=this.ty
    this.a = a*m.a + b*m.c; this.b = a*m.b + b*m.d
    this.c = c*m.a + d*m.c; this.d = c*m.b + d*m.d
    this.tx = tx*m.a + ty*m.c + m.tx; this.ty = tx*m.b + ty*m.d + m.ty
    return this
  }
  prepend(m) {
    const a=this.a, b=this.b, c=this.c, d=this.d, tx=this.tx, ty=this.ty
    this.a = m.a*a + m.b*c; this.b = m.a*b + m.b*d
    this.c = m.c*a + m.d*c; this.d = m.c*b + m.d*d
    this.tx = m.tx*a + m.ty*c + tx; this.ty = m.tx*b + m.ty*d + ty
    return this
  }
}

class Point {
  constructor(x = 0, y = 0) { this.x = x; this.y = y }
  clone() { return new Point(this.x, this.y) }
}

class ObservablePoint extends Point {
  constructor(cb, scope, x = 0, y = 0) { super(x, y); this.cb = cb; this.scope = scope }
  set(x, y = x) { this.x = x; this.y = y; this.cb?.call(this.scope) }
}

class Transform {
  constructor() { this.worldTransform = new Matrix() }
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

const urlResolve = (base, relative) => {
  if (/^https?:\/\//i.test(relative) || /^\//i.test(relative)) return relative
  const baseParts = base.split('/')
  baseParts.pop()
  return baseParts.join('/') + '/' + relative
}

// 立即设置全局 PIXI shim
const _g = typeof globalThis !== 'undefined' ? globalThis : self
if (!_g.PIXI) {
  _g.PIXI = {
    utils: { EventEmitter: MiniEmitter, url: { resolve: urlResolve } },
    EventEmitter: MiniEmitter,
    Matrix,
    Transform,
    Point,
    ObservablePoint,
    Texture,
    Container
  }
}

export { MiniEmitter, Matrix, Point, ObservablePoint, Transform, Container, Texture, urlResolve }
