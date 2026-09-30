function WindowControls({ onMinimize, onClose }) {
  return (
    <div className="window-controls" aria-label="Window controls">
      <button
        type="button"
        className="window-button minimize"
        onClick={onMinimize}
        aria-label="Minimize window"
      >
        -
      </button>
      <button type="button" className="window-button close" onClick={onClose} aria-label="Close window">
        X
      </button>
    </div>
  )
}

export default WindowControls