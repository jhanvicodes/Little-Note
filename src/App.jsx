import { useCallback, useEffect, useRef, useState } from 'react'
import './App.css'
import { RoomApiError, createRoom, joinRoom } from './api/roomsApi'
import catGrey from './assets/cat-grey.png'
import catPink from './assets/cat-pink.png'
import MessagePanel from './components/MessagePanel'
import RoomCodePanel from './components/RoomCodePanel'
import SetupPanel from './components/SetupPanel'
import WindowControls from './components/WindowControls'
import { SCREEN } from './constants'
import { useClipboardCopy } from './hooks/useClipboardCopy'
import { useRoomSocket } from './hooks/useRoomSocket'

// Dev-only helper: `?preview=note` opens the note screen with sample content so the
// pixel layout can be screenshotted without a backend. Always false in production.
const isPreviewNote =
  import.meta.env.DEV && new URLSearchParams(window.location.search).get('preview') === 'note'

function App() {
  const [screen, setScreen] = useState(isPreviewNote ? SCREEN.message : SCREEN.setup)
  const [name, setName] = useState('')
  const [roomCodeInput, setRoomCodeInput] = useState('')
  const [joinMode, setJoinMode] = useState(false)
  const [room, setRoom] = useState(
    isPreviewNote ? { roomCode: 'PREVIEW', roomId: 1, userId: 1, isCreator: true } : null,
  )
  const [latestNote, setLatestNote] = useState(isPreviewNote ? 'heloo' : '')
  const [noteText, setNoteText] = useState('')
  const [peerName, setPeerName] = useState('')
  const [error, setError] = useState('')
  const [isBusy, setIsBusy] = useState(false)

  const handleClose = () => {
    window.desktopSticker?.close()
  }

  const handleMinimize = () => {
    window.desktopSticker?.minimize()
  }

  // Sender-cat jump: the room creator owns the grey cat and the person who joined owns the
  // pink cat. A person's cat reacts whenever a note from them appears, in either window.
  // The `at` counter lets the same cat re-trigger reliably for back-to-back notes.
  const greyCatRef = useRef(null)
  const pinkCatRef = useRef(null)
  const [catJump, setCatJump] = useState(null)
  const appliedJumpRef = useRef(0)

  const makeCatJump = useCallback((cat) => {
    setCatJump((previous) => ({ cat, at: (previous?.at ?? 0) + 1 }))
  }, [])

  useEffect(() => {
    if (!catJump || catJump.at === appliedJumpRef.current) {
      // Ignore StrictMode's dev double-run; one jump per note.
      return
    }
    appliedJumpRef.current = catJump.at
    const cat = catJump.cat === 'grey' ? greyCatRef.current : pinkCatRef.current
    if (!cat) {
      return
    }
    cat.classList.remove('cat-jump')
    void cat.offsetWidth
    cat.classList.add('cat-jump')
  }, [catJump])

  const handleNoteReceived = useCallback((text, senderUserId, senderName) => {
    // One note at a time: the newest arrival replaces whatever was on screen.
    setLatestNote(text)
    // The sender's cat jumps; the receiving person's own cat stays put.
    if (!room) {
      return
    }
    if (senderName) {
      setPeerName(senderName)
    }
    const senderIsCreator =
      Number(senderUserId) === Number(room.userId) ? room.isCreator : !room.isCreator
    makeCatJump(senderIsCreator ? 'grey' : 'pink')
  }, [room, makeCatJump])

  // Each cat is named after its person: the grey one belongs to the room creator, the pink one
  // to whoever joined. Your own name is the one you typed; theirs rides along on their notes.
  const creatorName = room?.isCreator ? name : peerName
  const joinerName = room?.isCreator ? peerName : name

  const isMessaging = screen === SCREEN.roomCode || screen === SCREEN.message

  const handleConnected = useCallback(() => {
    // A live link clears any earlier "realtime connection unavailable" complaint.
    setError('')
  }, [])

  const { isConnected, sendNote } = useRoomSocket({
    roomCode: room?.roomCode,
    roomId: room?.roomId,
    userId: room?.userId,
    isActive: isMessaging && !isPreviewNote,
    onNoteReceived: handleNoteReceived,
    onConnected: handleConnected,
  })

  const openNoteScreen = useCallback(() => {
    setScreen(SCREEN.message)
  }, [])

  const { isCopied, error: copyError, copy, resetCopyState } = useClipboardCopy({
    text: room?.roomCode ?? '',
    onCopied: openNoteScreen,
  })

  useEffect(() => {
    if (isConnected && screen === SCREEN.message) {
      setError('')
    }
  }, [isConnected, screen])

  const enterRoom = (response, nextScreen, isCreator) => {
    setRoom({
      roomCode: response.roomCode,
      roomId: response.roomId,
      userId: response.userId,
      isCreator,
    })
    setLatestNote('')
    setNoteText('')
    setError('')
    setScreen(nextScreen)
  }

  const handleCreateRoom = async () => {
    if (isBusy || !name.trim()) {
      return
    }

    setIsBusy(true)
    setError('')

    try {
      const response = await createRoom(name.trim())
      resetCopyState()
      // The room code screen is the only stop before the sticker: copying it moves us on.
      enterRoom(response, SCREEN.roomCode, true)
    } catch (apiError) {
      setRoom(null)
      setError(apiError instanceof RoomApiError ? apiError.message : 'Unable to create room')
      setScreen(SCREEN.setup)
    } finally {
      setIsBusy(false)
    }
  }

  const handleJoinRoom = async () => {
    if (isBusy || !name.trim()) {
      return
    }

    if (!roomCodeInput.trim()) {
      setError('Room code is required')
      return
    }

    setIsBusy(true)
    setError('')

    try {
      const response = await joinRoom(name.trim(), roomCodeInput.trim())
      // Joining goes straight into the sticker, with no waiting screen in between.
      enterRoom(response, SCREEN.message, false)
    } catch (apiError) {
      setRoom(null)
      setError(apiError instanceof RoomApiError ? apiError.message : 'Unable to join room')
      setScreen(SCREEN.setup)
    } finally {
      setIsBusy(false)
    }
  }

  const handleSend = () => {
    const trimmed = noteText.trim()
    if (!trimmed) {
      return
    }

    if (!sendNote(trimmed)) {
      setError('Realtime connection unavailable')
      return
    }

    setNoteText('')
    setError('')
    // The note is away: the sender's own cat jumps right away in their window.
    makeCatJump(room?.isCreator ? 'grey' : 'pink')
  }

  const showJoinFlow = () => {
    setJoinMode(true)
    setRoomCodeInput('')
    setError('')
    setScreen(SCREEN.setup)
  }

  const showCreateFlow = () => {
    setJoinMode(false)
    setRoomCodeInput('')
    setError('')
    setScreen(SCREEN.setup)
  }

  const backToSetup = () => {
    setScreen(SCREEN.setup)
    setNoteText('')
    setError('')
  }

  const noteTabActive = screen === SCREEN.message || screen === SCREEN.roomCode

  const handleNoteTab = () => {
    // Only meaningful once a room exists; the room-code stop stays put until it is copied.
    if (room && screen === SCREEN.setup) {
      openNoteScreen()
    }
  }

  const handleSetupTab = () => {
    if (screen !== SCREEN.setup) {
      backToSetup()
    }
  }

  return (
    <div className="desktop-shell">
      <div className="sticker" role="dialog" aria-label="Little Note sticker">
        <div className="title-bar">
          <div className="title-text">
            LITTLE NOTE
            <svg
              className="title-heart"
              width="9"
              height="8"
              viewBox="0 0 7 6"
              shapeRendering="crispEdges"
              aria-hidden="true"
              focusable="false"
            >
              <rect x="1" y="0" width="2" height="1" />
              <rect x="4" y="0" width="2" height="1" />
              <rect x="0" y="1" width="7" height="2" />
              <rect x="1" y="3" width="5" height="1" />
              <rect x="2" y="4" width="3" height="1" />
              <rect x="3" y="5" width="1" height="1" />
            </svg>
          </div>
          <WindowControls onMinimize={handleMinimize} onClose={handleClose} />
        </div>

        <div className="scene">
          <div className="tab-row">
            <button
              type="button"
              className={`tab${noteTabActive ? ' active' : ''}`}
              aria-pressed={noteTabActive}
              onClick={handleNoteTab}
            >
              note
            </button>
            <button
              type="button"
              className={`tab${noteTabActive ? '' : ' active'}`}
              aria-pressed={!noteTabActive}
              onClick={handleSetupTab}
            >
              setup
            </button>
          </div>

          {screen === SCREEN.setup && (
            <SetupPanel
              name={name}
              onNameChange={setName}
              joinMode={joinMode}
              roomCode={roomCodeInput}
              onRoomCodeChange={setRoomCodeInput}
              onCreateRoom={handleCreateRoom}
              onJoinRoom={handleJoinRoom}
              onShowJoin={showJoinFlow}
              onShowCreate={showCreateFlow}
              error={error}
              isBusy={isBusy}
            />
          )}

          {screen === SCREEN.roomCode && (
            <RoomCodePanel
              roomCode={room?.roomCode ?? ''}
              isCopied={isCopied}
              error={copyError}
              onCopy={copy}
            />
          )}

          {screen === SCREEN.message && (
            <MessagePanel
              latestNote={latestNote}
              noteText={noteText}
              onNoteTextChange={setNoteText}
              onSend={handleSend}
              canSend={isPreviewNote || isConnected}
              error={error}
            />
          )}

          {screen === SCREEN.message && (
            <div className="cat-row" aria-hidden="true">
              <span className="cat-slot">
                <img className="cat" ref={greyCatRef} src={catGrey} alt="" draggable={false} />
                <span className="cat-name">{creatorName}</span>
              </span>
              <span className="cat-slot">
                <img className="cat" ref={pinkCatRef} src={catPink} alt="" draggable={false} />
                <span className="cat-name">{joinerName}</span>
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default App
