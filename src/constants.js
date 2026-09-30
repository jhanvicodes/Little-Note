/** Shared configuration for the Little Note sticker. */

/** Spring Boot backend that owns rooms and relays notes. */
export const API_HOST = 'little-note.onrender.com'
export const API_PORT = 443
export const API_BASE_URL = `https://${API_HOST}`

/** WebSocket path registered by WebSocketConfig on the backend. */
export const SOCKET_PATH = '/ws'

/** How long "Copied ✓" stays on screen before the sticker moves to the note screen. */
export const COPIED_FEEDBACK_MS = 850

/** Reconnect backoff for a dropped socket. */
export const RECONNECT_BASE_DELAY_MS = 400
export const RECONNECT_MAX_DELAY_MS = 4000

/** Matches the backend's own guard on note length. */
export const MAX_NOTE_LENGTH = 160
export const MAX_NAME_LENGTH = 18
export const MAX_ROOM_CODE_LENGTH = 12

/** The only two screens a paired user ever sees after setup. */
export const SCREEN = {
  setup: 'setup',
  roomCode: 'room-code',
  message: 'message',
}