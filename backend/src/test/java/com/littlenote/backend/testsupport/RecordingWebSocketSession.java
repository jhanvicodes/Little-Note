package com.littlenote.backend.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * A minimal in-memory {@link WebSocketSession} that records everything written to it.
 *
 * <p>Hand written on purpose: bytecode mocking of the {@code WebSocketSession} interface is not
 * available on every JDK, and the handler only needs identity, a URI, an attribute map and the
 * frames that were sent.
 */
public class RecordingWebSocketSession implements WebSocketSession {

    private final String id;
    private final URI uri;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<String> sentPayloads = new ArrayList<>();
    private final List<CloseStatus> closeStatuses = new ArrayList<>();
    private boolean open = true;

    public RecordingWebSocketSession(String id, URI uri) {
        this.id = id;
        this.uri = uri;
    }

    /** Convenience factory for the sticker's handshake URL. */
    public static RecordingWebSocketSession forRoom(String id, String roomCode, long roomId, long userId) {
        return new RecordingWebSocketSession(id, URI.create("ws://localhost:8080/ws?roomCode=" + roomCode
                + "&roomId=" + roomId + "&userId=" + userId));
    }

    public List<String> sentPayloads() {
        return List.copyOf(sentPayloads);
    }

    public List<CloseStatus> closeStatuses() {
        return List.copyOf(closeStatuses);
    }

    public boolean wasClosed() {
        return !closeStatuses.isEmpty();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // The handler never tunes the frame limit.
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 8192;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // The handler never tunes the frame limit.
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 8192;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (!open) {
            throw new IOException("Session " + id + " is closed");
        }
        sentPayloads.add(String.valueOf(message.getPayload()));
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        open = false;
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        open = false;
        closeStatuses.add(status);
    }
}
