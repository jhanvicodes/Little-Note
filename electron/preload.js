import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('desktopSticker', {
  close: () => ipcRenderer.send('close-window'),
  minimize: () => ipcRenderer.send('minimize-window'),
  copyText: (text) => ipcRenderer.invoke('copy-to-clipboard', text),
})
