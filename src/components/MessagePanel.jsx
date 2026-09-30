import { MAX_NOTE_LENGTH } from '../constants'

/**
 * The note screen. One speech box shows the latest note — whatever arrives replaces what
 * was on screen, and it reads "no note yet" while empty — plus the input row at the bottom
 * of the scene. The cats live in App (they belong to the scene, not this panel).
 */
function MessagePanel({ latestNote, noteText, onNoteTextChange, onSend, canSend, error }) {
  return (
    <>
      <div className="speech-wrap">
        <div className="speech-box">
          {latestNote || <span className="speech-empty">no note yet</span>}
        </div>
        <span className="speech-tail" aria-hidden="true" />
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="note-input-row">
        <input
          className="note-input"
          type="text"
          maxLength={MAX_NOTE_LENGTH}
          value={noteText}
          onChange={(event) => onNoteTextChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              onSend()
            }
          }}
          placeholder="your note"
        />
        <button
          type="button"
          className="send-button"
          onClick={onSend}
          aria-label="Send note"
          disabled={!canSend}
        >
          Send
        </button>
      </div>
    </>
  )
}

export default MessagePanel