import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'

// [Codex 降级接管] Encapsulates loading/placement/motion control for Live2D model.
export class Live2DManager {
  constructor(app) {
    this.app = app
    this.model = null
    this.state = {
      loaded: false,
      loading: false,
      error: null
    }
  }

  async load(modelJsonPath) {
    if (this.state.loading) {
      return this.model
    }

    this.state.loading = true
    this.state.error = null

    try {
      this.model = await Live2DModel.from(modelJsonPath)
      this.model.anchor.set(0.5, 0.5)
      this.app.stage.addChild(this.model)
      this.fitToScreen()
      this.state.loaded = true
      return this.model
    } catch (error) {
      this.state.error = error
      this.state.loaded = false
      this.renderFallback(error)
      return null
    } finally {
      this.state.loading = false
    }
  }

  fitToScreen() {
    if (!this.model) {
      return
    }

    const sw = this.app.renderer.width
    const sh = this.app.renderer.height

    const modelWidth = this.model.width || 1
    const modelHeight = this.model.height || 1
    const scale = Math.min((sw * 0.8) / modelWidth, (sh * 0.9) / modelHeight)

    this.model.scale.set(scale)
    this.model.x = sw / 2
    this.model.y = sh * 0.62
  }

  playMotion(group, index = 0) {
    if (!this.model?.motion) {
      return false
    }

    try {
      this.model.motion(group, index)
      return true
    } catch (_error) {
      return false
    }
  }

  setExpression(expression) {
    if (!this.model?.expression) {
      return false
    }

    try {
      this.model.expression(expression)
      return true
    } catch (_error) {
      return false
    }
  }

  getModel() {
    return this.model
  }

  getState() {
    return { ...this.state }
  }

  renderFallback(error) {
    if (!this.app?.stage) {
      return
    }

    const fallbackText = `Live2D model not available\n${error?.message || 'Unknown error'}`

    const text = new PIXI.Text(
      fallbackText,
      new PIXI.TextStyle({
        fill: 0xffffff,
        fontSize: 16,
        align: 'center'
      })
    )

    text.anchor.set(0.5)
    text.x = this.app.renderer.width / 2
    text.y = this.app.renderer.height / 2

    this.app.stage.addChild(text)
  }
}
