import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'

// Register the ticker for Live2D models
Live2DModel.registerTicker(PIXI.Ticker)

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
      this.model = await Live2DModel.from(modelJsonPath, {
        autoInteract: false,
        idleMotionGroup: 'Idle',
        ticker: this.app.ticker
      })

      // Set anchor to bottom-center for 'standing' effect
      this.model.anchor.set(0.5, 1.0)
      
      // Polyfill for PixiJS 7 EventSystem if needed
      if (!this.model.isInteractive) {
        this.model.isInteractive = () => !!this.model.interactive;
      }

      this.app.stage.addChild(this.model)
      this.fitToScreen()
      
      this.state.loaded = true
      return this.model
    } catch (error) {
      console.error('Failed to load Live2D model:', error)
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

    // --- Fixed Size Logic ---
    // Zoom in to show upper body only (Head & Shoulders)
    // Dynamic scaling: 2.8x screen height ensures high fidelity close-up
    const targetHeight = sh * 2.8
    
    // Get the base height of the model (at scale 1.0)
    const baseHeight = (this.model.height && this.model.height > 0) ? (this.model.height / this.model.scale.y) : 1000
    const scale = targetHeight / baseHeight

    this.model.scale.set(scale)
    this.model.x = sw / 2
    
    // Position the model to show the upper body centered
    // Strategy: Align the model's "Chest Center" to the screen's "Visual Center"
    
    // 1. Define Chest Center: Typically at 75% of model height from feet.
    //    We use 0.75 (75%) to target the chest/bust area.
    const chestCenterFromFeet = targetHeight * 0.75

    // 2. Define Visual Center: Where we want the chest to appear on screen.
    //    Move up from center (50%) to 35% to show more of the upper torso.
    const screenVisualCenter = sh * 0.35

    // 3. Calculate y (feet position)
    //    ScreenY = ModelY(Feet) - ChestHeight
    //    ModelY = ScreenY + ChestHeight
    this.model.y = screenVisualCenter + chestCenterFromFeet
  }

  playMotion(group, index = 0) {
    if (!this.model || !this.model.internalModel) {
      return false
    }

    try {
      this.model.internalModel.motionManager.startMotion(group, index)
      return true
    } catch (_error) {
      return false
    }
  }

  setExpression(expression) {
    if (!this.model) {
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
