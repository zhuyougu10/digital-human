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

export async function playAuthenticatedAudio({ audioUrl, token, onLoadedMetadata } = {}) {
  if (!audioUrl) {
    throw new Error('audioUrl is required')
  }
  if (!token) {
    throw new Error('token is required')
  }

  const response = await fetch(audioUrl, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const audioBlob = await response.blob()
  const objectUrl = URL.createObjectURL(audioBlob)
  const audio = ensureAudio()
  audio.src = objectUrl

  let revoked = false
  const revokeObjectUrl = () => {
    if (revoked) {
      return
    }
    revoked = true
    URL.revokeObjectURL(objectUrl)
  }

  audio.onended = revokeObjectUrl
  audio.onerror = () => {
    revokeObjectUrl()
  }
  audio.onloadedmetadata = () => {
    onLoadedMetadata?.(audio)
  }

  try {
    await audio.play()
  } catch (error) {
    revokeObjectUrl()
    throw error
  }

  return audio
}
