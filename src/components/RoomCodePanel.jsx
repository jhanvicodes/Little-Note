/**
 * Shown once, right after a room is created. Copying the code is the only action: as soon as the
 * "Copied ✓" confirmation has been read the sticker moves itself to the note screen, so there is
 * no Continue button and this screen never comes back. Rendered inside the scene as a cream
 * pixel box; the cats stay on the note screen only.
 */
function RoomCodePanel({ roomCode, isCopied, error, onCopy }) {
  return (
    <div className="room-box">
      <div className="room-label">room code</div>
      <div className="room-code">{roomCode}</div>
      <button type="button" className="copy-button" onClick={onCopy} disabled={isCopied}>
        {isCopied ? 'Copied ✓' : 'Copy'}
      </button>
      <div className="room-status">
        {isCopied ? 'opening your note…' : 'copy it and send to your person'}
      </div>

      {error && <div className="error-message">{error}</div>}
    </div>
  )
}

export default RoomCodePanel