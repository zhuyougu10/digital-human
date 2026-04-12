export class Live2dLipSync {
  constructor(setMouthValue) {
    this.setMouthValue = setMouthValue
    this.isPlaying = false
    this.autoStopTimer = null
  }

  start(duration = 0) {
    this.isPlaying = true

    if (duration > 0) {
      if (this.autoStopTimer) {
        clearTimeout(this.autoStopTimer)
      }
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

  update() {
    if (!this.isPlaying) {
      return
    }

    const now = Date.now()
    const value = Math.sin(now / 80) * 0.3 + Math.sin(now / 150) * 0.2 + 0.5
    this.setMouthValue(Math.max(0, Math.min(1, value)))
  }
}
