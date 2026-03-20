let sharedAudio = null

function ensureAudio() {
  if (sharedAudio) {
    return sharedAudio
  }
  sharedAudio = new Audio()
  sharedAudio.preload = 'auto'
  sharedAudio.playsInline = true
  return sharedAudio
}

export async function unlockAudioPlayback() {
  const audio = ensureAudio()
  if (audio.dataset?.unlocked === 'true') {
    return
  }

  const originalMuted = audio.muted
  const originalSrc = audio.src

  audio.muted = true
  audio.src = 'data:audio/mp3;base64,//uQxAAAAAAAAAAAAAAAAAAAAAAASW5mbwAAAA8AAAAFAAAGAAACcQCA'

  try {
    await audio.play()
    audio.pause()
    audio.currentTime = 0
    if (audio.dataset) {
      audio.dataset.unlocked = 'true'
    }
  } catch (error) {
    if (error?.name === 'NotSupportedError') {
      return
    }
    throw error
  } finally {
    audio.muted = originalMuted
    audio.src = originalSrc
  }
}

export function playAuthenticatedAudio({ audioUrl, token, onLoadedMetadata } = {}) {
  if (!audioUrl) return Promise.reject(new Error('audioUrl is required'))
  if (!token) return Promise.reject(new Error('token is required'))

  return new Promise((resolve, reject) => {
    fetch(audioUrl, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`
      }
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return response.blob()
      })
      .then(audioBlob => {
        const objectUrl = URL.createObjectURL(audioBlob)
        const audio = ensureAudio()
        audio.src = objectUrl

        let revoked = false
        const revokeObjectUrl = () => {
          if (revoked) return
          revoked = true
          URL.revokeObjectURL(objectUrl)
        }

        audio.onended = () => {
          revokeObjectUrl()
          resolve(audio)
        }
        audio.onerror = (e) => {
          revokeObjectUrl()
          reject(e)
        }
        audio.onloadedmetadata = () => {
          onLoadedMetadata?.(audio)
        }

        audio.play().catch(err => {
          revokeObjectUrl()
          reject(err)
        })
      })
      .catch(error => {
        reject(error)
      })
  })
}
