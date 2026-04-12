import { createCanvasImage, setupWeappCanvasAdapter } from './weapp-canvas-adapter'

const DEFAULT_MODEL_FILE = 'wariza.model3.json'
const GL_CONTEXT_UID = 1

// 模型资源通过 HTTP 从 live2d-h5 容器或 CDN 加载
const MODEL_BASE_URL = import.meta.env.VITE_LIVE2D_BASE || 'http://192.168.31.210:8090'

const joinUrl = (base, file = '') => {
  const normalizedBase = base.replace(/\/+$/, '')
  const normalizedFile = file.replace(/^\/+/, '')
  return normalizedFile ? `${normalizedBase}/${normalizedFile}` : normalizedBase
}

const httpGet = (url, responseType = 'text') =>
  new Promise((resolve, reject) => {
    wx.request({
      url,
      method: 'GET',
      responseType,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${url}`))
        }
      },
      fail: reject
    })
  })

const readJson = async (url) => {
  const data = await httpGet(url)
  return typeof data === 'string' ? JSON.parse(data) : data
}

const readArrayBuffer = async (url) => httpGet(url, 'arraybuffer')

const loadImage = (canvas, imageUrl) =>
  new Promise((resolve, reject) => {
    const image = createCanvasImage(canvas)
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = imageUrl
  })

const createTexture = (gl, image) => {
  const texture = gl.createTexture()
  gl.bindTexture(gl.TEXTURE_2D, texture)
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 1)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image)
  gl.bindTexture(gl.TEXTURE_2D, null)
  return texture
}

const resolveSessionOption = (settings) => ({
  idleMotionGroup: 'Idle',
  motionPreload: 'NONE',
  expressionFadingDuration: 500,
  settings
})

const parseSessionId = (payload) => payload?.sessionId || payload?.id || payload?.data?.id || payload?.data?.sessionId || ''

export class CubismRenderer {
  constructor() {
    this.canvas = null
    this.gl = null
    this.runtime = null
    this.internalModel = null
    this.settings = null
    this.textures = []
    this.motionCache = new Map()
    this.expressionCache = new Map()
    this.rafId = null
    this.lastTimestamp = 0
    this.modelBaseUrl = ''
    this.world = {
      scale: 1,
      x: 0,
      y: 0
    }
  }

  async loadModel(canvasNode, modelDir, modelFile = DEFAULT_MODEL_FILE) {
    this.destroy()

    this.canvas = canvasNode
    this.gl = canvasNode.getContext('webgl', {
      alpha: true,
      antialias: true,
      premultipliedAlpha: true
    })

    if (!this.gl) {
      throw new Error('Unable to acquire WebGL context from mini program canvas.')
    }

    this.runtime = await setupWeappCanvasAdapter(canvasNode)
    await this.runtime.cubism4Ready()

    // modelDir 可以是完整 URL 或相对路径
    this.modelBaseUrl = modelDir.startsWith('http')
      ? modelDir
      : joinUrl(MODEL_BASE_URL, modelDir)

    const modelJsonUrl = joinUrl(this.modelBaseUrl, modelFile)
    const json = await readJson(modelJsonUrl)
    json.url = modelJsonUrl

    const settings = new this.runtime.Cubism4ModelSettings(json)
    settings.resolveURL = (file) => joinUrl(this.modelBaseUrl, file)
    this.settings = settings

    const mocBuffer = await readArrayBuffer(settings.resolveURL(settings.moc))
    const moc = this.runtime.CubismMoc.create(mocBuffer)
    const coreModel = moc.createModel()
    coreModel.__moc = moc

    this.internalModel = new this.runtime.Cubism4InternalModel(
      coreModel,
      settings,
      resolveSessionOption(settings)
    )

    this.internalModel.__moc = moc

    this.patchLoaders()
    await this.loadPhysics()
    await this.bindTextures()

    this.internalModel.updateWebGLContext(this.gl, GL_CONTEXT_UID)
    this.fitToScreen()
    this.startLoop()
    await this.playMotion('Idle', 0)

    return this
  }

  patchLoaders() {
    const motionManager = this.internalModel.motionManager
    motionManager._loadMotion = async (group, index) => {
      const key = `${group}:${index}`
      if (this.motionCache.has(key)) {
        return this.motionCache.get(key)
      }

      const definition = motionManager.definitions[group]?.[index]
      if (!definition?.File) {
        return undefined
      }

      const data = await readJson(this.settings.resolveURL(definition.File))
      const motion = motionManager.createMotion(data, group, definition)
      this.motionCache.set(key, motion)
      return motion
    }

    const expressionManager = motionManager.expressionManager
    if (!expressionManager) {
      return
    }

    expressionManager._loadExpression = async (index) => {
      if (this.expressionCache.has(index)) {
        return this.expressionCache.get(index)
      }

      const definition = expressionManager.definitions[index]
      if (!definition?.File) {
        return undefined
      }

      const data = await readJson(this.settings.resolveURL(definition.File))
      const expression = expressionManager.createExpression(data, definition)
      this.expressionCache.set(index, expression)
      return expression
    }
  }

  async loadPhysics() {
    if (!this.settings.physics) {
      return
    }

    const data = await readJson(this.settings.resolveURL(this.settings.physics))
    this.internalModel.physics = this.runtime.CubismPhysics.create(data)
  }

  async bindTextures() {
    const images = await Promise.all(
      this.settings.textures.map((texturePath) =>
        loadImage(this.canvas, this.settings.resolveURL(texturePath))
      )
    )

    this.textures = images.map((image, index) => {
      const texture = createTexture(this.gl, image)
      this.internalModel.bindTexture(index, texture)
      return texture
    })
  }

  resize(width, height, pixelRatio = 1) {
    if (!this.canvas || !this.gl) {
      return
    }

    this.canvas.width = Math.max(1, Math.floor(width * pixelRatio))
    this.canvas.height = Math.max(1, Math.floor(height * pixelRatio))
    this.gl.viewport(0, 0, this.canvas.width, this.canvas.height)
    this.fitToScreen()
  }

  fitToScreen() {
    if (!this.internalModel || !this.canvas) {
      return
    }

    const sw = this.canvas.width
    const sh = this.canvas.height
    const baseHeight = this.internalModel.height || this.internalModel.originalHeight || 1000
    const baseWidth = this.internalModel.width || this.internalModel.originalWidth || 1000
    const targetHeight = sh * 2.8
    const scale = targetHeight / baseHeight
    const chestCenterFromFeet = targetHeight * 0.75
    const screenVisualCenter = sh * 0.35
    const feetY = screenVisualCenter + chestCenterFromFeet

    this.world.scale = scale
    this.world.x = sw / 2 - (baseWidth * scale) / 2
    this.world.y = feetY - targetHeight
  }

  getProjectionMatrix() {
    const matrix = new globalThis.PIXI.Matrix()
    matrix.a = 2 / this.canvas.width
    matrix.d = -2 / this.canvas.height
    matrix.tx = -1
    matrix.ty = 1

    const world = new globalThis.PIXI.Matrix()
    world.a = this.world.scale
    world.d = this.world.scale
    world.tx = this.world.x
    world.ty = this.world.y

    return matrix.append(world)
  }

  startLoop() {
    this.lastTimestamp = 0

    const tick = (timestamp) => {
      if (!this.internalModel || !this.gl) {
        return
      }

      const now = timestamp || Date.now()
      const delta = this.lastTimestamp ? now - this.lastTimestamp : 16
      this.lastTimestamp = now

      this.gl.viewport(0, 0, this.canvas.width, this.canvas.height)
      this.gl.clearColor(0, 0, 0, 0)
      this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT)

      this.internalModel.viewport = [0, 0, this.canvas.width, this.canvas.height]
      this.internalModel.update(delta, now)
      this.internalModel.updateTransform(this.getProjectionMatrix())
      this.internalModel.draw(this.gl)

      this.rafId = this.canvas.requestAnimationFrame
        ? this.canvas.requestAnimationFrame(tick)
        : setTimeout(() => tick(Date.now()), 16)
    }

    tick(Date.now())
  }

  async playMotion(group, index = 0) {
    if (!this.internalModel?.motionManager) {
      return false
    }

    return this.internalModel.motionManager.startMotion(group, index)
  }

  async setExpression(name) {
    const expressionManager = this.internalModel?.motionManager?.expressionManager
    if (!expressionManager) {
      return false
    }

    return expressionManager.setExpression(name)
  }

  setMouthOpenY(value) {
    if (!this.internalModel?.coreModel) {
      return
    }

    this.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', Math.max(0, Math.min(1, value)))
  }

  destroy() {
    if (this.canvas && this.rafId) {
      if (this.canvas.cancelAnimationFrame) {
        this.canvas.cancelAnimationFrame(this.rafId)
      } else {
        clearTimeout(this.rafId)
      }
    }

    this.rafId = null

    if (this.gl) {
      this.textures.forEach((texture) => this.gl.deleteTexture(texture))
    }

    this.textures = []
    this.motionCache.clear()
    this.expressionCache.clear()

    if (this.internalModel) {
      this.internalModel.destroy()
      this.internalModel = null
    }
  }
}

export const createCubismRenderer = () => new CubismRenderer()
export { parseSessionId }
