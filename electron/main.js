import { app, BrowserWindow, clipboard, ipcMain } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

process.on('uncaughtException', (error) => {
  console.error('Electron uncaught exception during startup:', error)
})

process.on('unhandledRejection', (reason) => {
  console.error('Electron unhandled rejection during startup:', reason)
})

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const isDev = !app.isPackaged
const stateFile = path.join(app.getPath('userData'), 'little-note-window-state.json')

const defaultBounds = { x: 140, y: 120, width: 250, height: 290 }

function loadWindowState() {
  try {
    const raw = fs.readFileSync(stateFile, 'utf8')
    const parsed = JSON.parse(raw)

    return {
      x: parsed.x ?? defaultBounds.x,
      y: parsed.y ?? defaultBounds.y,
      width: parsed.width ?? defaultBounds.width,
      height: parsed.height ?? defaultBounds.height,
    }
  } catch {
    return { ...defaultBounds }
  }
}

function saveWindowState(window) {
  const bounds = window.getBounds()

  fs.writeFileSync(
    stateFile,
    JSON.stringify({
      x: bounds.x,
      y: bounds.y,
      width: bounds.width,
      height: bounds.height,
    }),
  )
}

let mainWindow

function createWindow() {
  const savedBounds = loadWindowState()

  mainWindow = new BrowserWindow({
    x: savedBounds.x,
    y: savedBounds.y,
    width: 250,
    height: 290,
    minWidth: 250,
    minHeight: 290,
    maxWidth: 250,
    maxHeight: 290,
    frame: false,
    transparent: true,
    resizable: false,
    show: false,
    roundedCorners: true,
    hasShadow: true,
    focusable: true,
    movable: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    backgroundColor: '#00000000',
    title: 'Little Note',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mainWindow.on('move', () => saveWindowState(mainWindow))
  mainWindow.on('resize', () => saveWindowState(mainWindow))
  mainWindow.on('closed', () => {
    mainWindow = null
  })
  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
  })

  mainWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  mainWindow.setAlwaysOnTop(true, 'floating')

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }
}

app.whenReady().then(() => {
  try {
    createWindow()
  } catch (error) {
    console.error('Failed to create Electron window:', error)
    app.quit()
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      try {
        createWindow()
      } catch (error) {
        console.error('Failed to recreate Electron window:', error)
        app.quit()
      }
    }
  })
})

ipcMain.on('close-window', () => {
  app.quit()
})

ipcMain.on('minimize-window', () => {
  if (mainWindow) {
    mainWindow.minimize()
  }
})

ipcMain.handle('copy-to-clipboard', (_event, text) => {
  if (typeof text !== 'string') {
    return false
  }

  clipboard.writeText(text)
  return true
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
