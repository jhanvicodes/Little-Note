import { useCallback, useEffect, useRef, useState } from 'react'
import {
  API_HOST,
  API_PORT,
  RECONNECT_BASE_DELAY_MS,
  RECONNECT_MAX_DELAY_MS,
  SOCKET_PATH,
} from '../constants'

export const SOCKET_STATUS = {
  closed: 'closed',
  connecting: 'connecting',
  open: 'open',
}

function buildSocketUrl(roomCode, roomId, userId) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.hostname || API_HOST
  const query = new URLSearchParams({
    roomCode,
    roomId: String(roomId),
    userId: String(userId),
  })
  return `${protocol}//${host}:${API_PORT}${SOCKET_PATH}?${query.toString()}`
}

/**
 * A frame only belongs on screen when it is a note, it is for this room, and somebody else wrote
 * it. The backend never echoes a sender their own note; this check keeps that guarantee true even
 * if the same user id ever holds two windows open.
 */
function isNoteFromTheOtherPerson(payload, { roomCode, roomId, userId }) {
  return payload?.type === 'message'
    && Number(payload.roomId) === Number(roomId)
    && String(payload.roomCode ?? '').trim() === String(roomCode).trim()
    && Number(payload.userId) !== Number(userId)
}

/**
 * Keeps one WebSocket session alive for the paired room and reports every note written by the
 * other person. Only the latest note matters, so incoming notes simply replace the previous one.
 */
export function useRoomSocket({ roomCode, roomId, userId, isActive, onNoteReceived, onConnected }) {
  const [liveStatus, setLiveStatus] = useState(SOCKET_STATUS.closed)
  const socketRef = useRef(null)
  const reconnectTimerRef = useRef(null)
  const onNoteReceivedRef = useRef(onNoteReceived)
  const onConnectedRef = useRef(onConnected)

  useEffect(() => {
    onNoteReceivedRef.current = onNoteReceived
    onConnectedRef.current = onConnected
  }, [onNoteReceived, onConnected])

  const hasIdentity = Boolean(roomCode) && roomId != null && userId != null

  useEffect(() => {
    if (!isActive || !hasIdentity) {
      // Nothing to synchronise while the sticker is not on a room screen.
      return undefined
    }

    let isDisposed = false
    let attempt = 0

    function clearReconnectTimer() {
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
    }

    function scheduleReconnect() {
      if (isDisposed) {
        return
      }
      const delay = Math.min(RECONNECT_BASE_DELAY_MS * 2 ** attempt, RECONNECT_MAX_DELAY_MS)
      attempt += 1
      console.info('[little note] socket reconnecting in', delay, 'ms')
      reconnectTimerRef.current = setTimeout(connect, delay)
    }

    function connect() {
      if (isDisposed) {
        return
      }

      const url = buildSocketUrl(roomCode, roomId, userId)
      console.info('[little note] socket connecting', url)

      let socket
      try {
        socket = new WebSocket(url)
      } catch (error) {
        console.error('[little note] socket could not be created', error)
        scheduleReconnect()
        return
      }

      socketRef.current = socket
      setLiveStatus(SOCKET_STATUS.connecting)

      socket.onopen = () => {
        attempt = 0
        setLiveStatus(SOCKET_STATUS.open)
        console.info('[little note] socket open', { roomId, userId })
        onConnectedRef.current?.()
      }

      socket.onmessage = (event) => {
        let payload
        try {
          payload = JSON.parse(event.data)
        } catch {
          console.warn('[little note] ignored a frame that was not JSON')
          return
        }

        if (!isNoteFromTheOtherPerson(payload, { roomCode, roomId, userId })) {
          return
        }

        console.info('[little note] note received from your person', { fromUserId: payload.userId })
        onNoteReceivedRef.current?.(payload.text ?? '', Number(payload.userId), payload.username ?? '')
      }

      socket.onerror = () => {
        console.warn('[little note] socket error', url)
      }

      socket.onclose = (event) => {
        if (socketRef.current === socket) {
          socketRef.current = null
        }
        setLiveStatus(SOCKET_STATUS.closed)
        console.info('[little note] socket closed', { code: event.code })
        scheduleReconnect()
      }
    }

    connect()

    return () => {
      isDisposed = true
      clearReconnectTimer()

      const socket = socketRef.current
      socketRef.current = null
      if (socket) {
        // Detach first so the deliberate teardown does not trigger a reconnect.
        socket.onopen = null
        socket.onmessage = null
        socket.onerror = null
        socket.onclose = null
        socket.close()
      }
    }
    // `hasIdentity` is derived from the three identity values below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isActive, hasIdentity, roomCode, roomId, userId])

  const sendNote = useCallback(
    (text) => {
      const socket = socketRef.current
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        console.warn('[little note] cannot send, socket is not open')
        return false
      }

      socket.send(
        JSON.stringify({
          type: 'message',
          roomCode,
          roomId: Number(roomId),
          userId: Number(userId),
          text,
        }),
      )
      console.info('[little note] note sent to your person')
      return true
    },
    [roomCode, roomId, userId],
  )

  const status = isActive && hasIdentity ? liveStatus : SOCKET_STATUS.closed

  return { status, isConnected: status === SOCKET_STATUS.open, sendNote }
}
