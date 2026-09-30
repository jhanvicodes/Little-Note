package com.littlenote.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.littlenote.backend.websocket.RoomWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * The single handler instance that owns every live sticker session. It is a component-scanned
     * singleton injected here rather than built inline so the session registry inside the handler
     * is shared across the app.
     */
    private final RoomWebSocketHandler roomWebSocketHandler;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler) {
        this.roomWebSocketHandler = roomWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // The sticker runs at http://localhost:5173 in development and inside a file://
        // document in the packaged Electron build, which reports a "null" origin. Origin
        // patterns accept both instead of failing the handshake with a 403.
        registry.addHandler(roomWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}
