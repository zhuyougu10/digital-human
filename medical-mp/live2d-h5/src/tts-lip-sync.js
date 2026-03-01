// [Codex 降级接管] TTS lip-sync simulator for Live2D mouth parameter.
export class LipSyncManager {
  constructor(modelProvider) {
    this.modelProvider = modelProvider
    this.animationId = null
    this.isPlaying = false
  }

  start() {
    if (this.isPlaying) {
      return
    }
    this.isPlaying = true
    this.animate()
  }

  stop() {
    this.isPlaying = false

    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId)
      this.animationId = null
    }

    this.setMouthValue(0)
  }

  animate() {
    if (!this.isPlaying) {
      return
    }

    const value = Math.sin(Date.now() / 100) * 0.5 + 0.5
    this.setMouthValue(value)

    this.animationId = requestAnimationFrame(() => this.animate())
  }

  setMouthValue(value) {
    const model = this.modelProvider?.()
    const coreModel = model?.internalModel?.coreModel
    if (!coreModel?.setParameterValueById) {
      return
    }

    coreModel.setParameterValueById('ParamMouthOpenY', value)
  }
}
