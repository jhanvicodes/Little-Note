import { useCallback, useEffect, useRef, useState } from 'react'
import { COPIED_FEEDBACK_MS } from '../constants'

/**
 * Writes text to the clipboard, preferring the Electron bridge so the copy also works inside the
 * packaged file:// window where the browser clipboard API is unavailable.
 */
async function writeToClipboard(text) {
  if (window.desktopSticker?.copyText) {
    const copied = await window.desktopSticker.copyText(text)
    if (copied) {
      return true
    }
  }

  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return true
  }

  return false
}

/**
 * Copies text and then reports success. There is no Continue button in the sticker, so the caller
 * uses the copied signal to move on by itself once the "Copied ✓" confirmation has been read.
 */
export function useClipboardCopy({ text, onCopied, feedbackMs = COPIED_FEEDBACK_MS }) {
  const [isCopied, setIsCopied] = useState(false)
  const [error, setError] = useState('')
  const feedbackTimerRef = useRef(null)

  useEffect(() => {
    return () => {
      if (feedbackTimerRef.current) {
        clearTimeout(feedbackTimerRef.current)
      }
    }
  }, [])

  const copy = useCallback(async () => {
    if (!text) {
      return
    }

    try {
      const copied = await writeToClipboard(text)
      if (!copied) {
        throw new Error('Clipboard unavailable')
      }

      setError('')
      setIsCopied(true)

      if (feedbackTimerRef.current) {
        clearTimeout(feedbackTimerRef.current)
      }
      feedbackTimerRef.current = setTimeout(() => {
        setIsCopied(false)
        onCopied?.()
      }, feedbackMs)
    } catch {
      setError('Clipboard unavailable')
    }
  }, [text, onCopied, feedbackMs])

  const resetCopyState = useCallback(() => {
    if (feedbackTimerRef.current) {
      clearTimeout(feedbackTimerRef.current)
      feedbackTimerRef.current = null
    }
    setIsCopied(false)
  }, [])

  return { isCopied, error, copy, resetCopyState }
}
