import { API_BASE_URL } from '../constants'

/**
 * Thin client for the existing room endpoints. The backend contract is unchanged:
 * POST /api/rooms/create -> { roomCode, username, userId, roomId }
 * POST /api/rooms/join   -> { roomCode, username, userId, roomId }
 */

export class RoomApiError extends Error {
  constructor(message) {
    super(message)
    this.name = 'RoomApiError'
  }
}

async function postJson(path, body) {
  let response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new RoomApiError('Backend unavailable')
  }

  const payload = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw new RoomApiError(payload.error || 'Something went wrong')
  }

  return payload
}

export function createRoom(username) {
  return postJson('/api/rooms/create', { username })
}

export function joinRoom(username, roomCode) {
  return postJson('/api/rooms/join', { username, roomCode })
}
