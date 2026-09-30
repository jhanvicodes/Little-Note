import catGrey from '../assets/cat-grey.png'
import catPink from '../assets/cat-pink.png'
import { MAX_NAME_LENGTH, MAX_ROOM_CODE_LENGTH } from '../constants'

/**
 * Chunky pixel heart, drawn as unit squares so it stays crisp at any size.
 * The big one between the cats pumps gently forever; the small ones sit beside it.
 */
function PixelHeart({ className }) {
  return (
    <svg
      className={className}
      viewBox="0 0 9 8"
      shapeRendering="crispEdges"
      aria-hidden="true"
      focusable="false"
    >
      <rect x="2" y="0" width="2" height="1" />
      <rect x="5" y="0" width="2" height="1" />
      <rect x="1" y="1" width="7" height="2" />
      <rect x="0" y="3" width="9" height="1" />
      <rect x="1" y="4" width="7" height="1" />
      <rect x="2" y="5" width="5" height="1" />
      <rect x="3" y="6" width="3" height="1" />
      <rect x="4" y="7" width="1" height="1" />
    </svg>
  )
}

/**
 * The landing page: two pixel cats and a beating heart on a purple night, the name field and
 * the two ways in on a cream panel below. Creating and joining keep calling the same handlers
 * the sticker has always used, and the note screen is untouched.
 */
function SetupPanel({
  name,
  onNameChange,
  joinMode,
  roomCode,
  onRoomCodeChange,
  onCreateRoom,
  onJoinRoom,
  onShowJoin,
  onShowCreate,
  error,
  isBusy,
}) {
  const submitOnEnter = (event, action) => {
    if (event.key === 'Enter') {
      action()
    }
  }

  return (
    <div className="setup-panel">
      <div className="landing-stage">
        <span className="landing-spark landing-spark-a" />
        <span className="landing-spark landing-spark-b" />
        <span className="landing-spark landing-spark-c" />

        <div className="setup-header">
          <div className="setup-subtitle">
            a tiny private space for{' '}
            <br />
            two
          </div>
        </div>

        <div className="landing-cats">
          <img className="landing-cat" src={catGrey} alt="" draggable={false} />
          <div className="landing-hearts">
            <PixelHeart className="landing-heart landing-heart-tiny" />
            <PixelHeart className="landing-heart landing-heart-beat" />
            <PixelHeart className="landing-heart landing-heart-echo" />
          </div>
          <img className="landing-cat" src={catPink} alt="" draggable={false} />
        </div>
      </div>

      <div className="landing-ground">
        <span className="landing-step landing-step-left" />
        <span className="landing-step landing-step-right" />

        <div className="landing-form">
          {error && <div className="error-message">{error}</div>}

          <label className="field-label" htmlFor="name-input">
            your name
          </label>
          <input
            id="name-input"
            className="name-input"
            type="text"
            maxLength={MAX_NAME_LENGTH}
            value={name}
            onChange={(event) => onNameChange(event.target.value)}
            onKeyDown={(event) => submitOnEnter(event, joinMode ? onJoinRoom : onCreateRoom)}
            placeholder="your name"
          />

          {joinMode && (
            <>
              <label className="field-label" htmlFor="room-code-input">
                room code
              </label>
              <input
                id="room-code-input"
                className="name-input"
                type="text"
                maxLength={MAX_ROOM_CODE_LENGTH}
                value={roomCode}
                onChange={(event) => onRoomCodeChange(event.target.value.toUpperCase())}
                onKeyDown={(event) => submitOnEnter(event, onJoinRoom)}
                placeholder="room code"
              />
            </>
          )}

          <div className="action-stack">
            {!joinMode ? (
              <>
                <button type="button" className="primary-button" onClick={onCreateRoom} disabled={isBusy}>
                  {isBusy ? 'Creating…' : 'Create Room'}
                </button>
                <button type="button" className="secondary-button" onClick={onShowJoin}>
                  Join Room
                </button>
              </>
            ) : (
              <>
                <button type="button" className="primary-button" onClick={onJoinRoom} disabled={isBusy}>
                  {isBusy ? 'Joining…' : 'Join Room'}
                </button>
                <button type="button" className="secondary-button" onClick={onShowCreate}>
                  Back
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      <span className="landing-strip" />
    </div>
  )
}

export default SetupPanel
