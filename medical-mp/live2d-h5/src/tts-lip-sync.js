// [Codex 降级接管] TTS lip-sync simulator for Live2D mouth parameter.
// Improved to use model's update cycle for better reliability.
export class LipSyncManager {
  constructor(modelProvider) {
    this.modelProvider = modelProvider
    this.isPlaying = false
    this.autoStopTimer = null
  }

  start(duration = 0) {
    this.isPlaying = true
    
    if (duration > 0) {
      if (this.autoStopTimer) clearTimeout(this.autoStopTimer)
      this.autoStopTimer = setTimeout(() => this.stop(), duration)
    }
  }

  stop() {
    this.isPlaying = false
    if (this.autoStopTimer) {
      clearTimeout(this.autoStopTimer)
      this.autoStopTimer = null
    }
    this.setMouthValue(0)
  }

  // This should be called every frame from the external Ticker or model update
  update() {
    if (!this.isPlaying) {
      return
    }

    // Generate a natural-looking mouth movement using multiple sine waves
    const now = Date.now()
    const value = (Math.sin(now / 80) * 0.3 + Math.sin(now / 150) * 0.2 + 0.5)
    this.setMouthValue(Math.max(0, Math.min(1, value)))
  }

  setMouthValue(value) {
    const model = this.modelProvider?.()
    if (!model || !model.internalModel || !model.internalModel.coreModel) {
      return
    }

    // Use setParameterValueById which is the standard Cubism way
    model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', value)
  }
}
