package com.littlenote.backend.websocket;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.littlenote.backend.websocket.RoomMessageRepository.Member;
import com.littlenote.backend.websocket.RoomMessageRepository.RoomMessage;

/**
 * Routes the single latest note of a two-person room.
 *
 * <p>Connection: {@code /ws?roomCode=..&roomId=..&userId=..}. The sticker opens one session per
 * window. A note sent by one member is stored as the room's latest message and pushed to the
 * other member's open sessions only, so the sender never renders their own note.
 */
@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private static final String ROOM_CODE = "roomCode";
    private static final String ROOM_ID = "roomId";
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";

    private static final String TYPE = "type";
    private static final String TEXT = "text";
    private static final String MESSAGE_TYPE = "message";
    private static final int MAX_TEXT_LENGTH = 160;

    private final RoomMessageService roomMessageService;
    private final ObjectMapper objectMapper;

    /** Every open sticker connection grouped by room id, so a note can be routed to the peer. */
    private final Map<Long, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(RoomMessageService roomMessageService, ObjectMapper objectMapper) {
        this.roomMessageService = roomMessageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> query = extractQuery(session);
        String roomCode = query.getOrDefault(ROOM_CODE, "");
        long roomId = parseId(query.get(ROOM_ID));
        long userId = parseId(query.get(USER_ID));

        Optional<Member> member = roomMessageService.findMember(roomCode, roomId, userId);
        if (member.isEmpty()) {
            LOGGER.warn("Rejected WebSocket connection, not a room member: session={}, roomCode={}, roomId={}, userId={}",
                    session.getId(), roomCode, roomId, userId);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Room membership is invalid"));
            return;
        }

        session.getAttributes().put(ROOM_CODE, roomCode);
        session.getAttributes().put(ROOM_ID, roomId);
        session.getAttributes().put(USER_ID, userId);
        session.getAttributes().put(USERNAME, member.get().username());
        Set<WebSocketSession> roomSessions =
                sessionsByRoom.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet());
        roomSessions.add(session);

        LOGGER.info("WebSocket opened: session={}, roomCode={}, roomId={}, userId={}, openSessionsInRoom={}",
                session.getId(), roomCode, roomId, userId, roomSessions.size());

        replayLatestMessage(session, roomId, userId);
    }

    /**
     * Hands the room's stored latest note to a freshly connected member, unless they wrote it.
     * This is what delivers the first note of a room: it was sent before anybody was listening.
     */
    private void replayLatestMessage(WebSocketSession session, long roomId, long userId) {
        Optional<RoomMessage> latest = roomMessageService.getLatestMessage(roomId);
        if (latest.isEmpty()) {
            LOGGER.info("Nothing to replay, room has no note yet: roomId={}, userId={}", roomId, userId);
            return;
        }

        RoomMessage message = latest.get();
        if (message.userId() == userId) {
            LOGGER.info("Skipped replay of own note: roomId={}, userId={}", roomId, userId);
            return;
        }

        try {
            sendMessage(session, message);
            LOGGER.info("Replayed latest note: roomId={}, toUserId={}, fromUserId={}, chars={}",
                    roomId, userId, message.userId(), message.text().length());
        } catch (IOException exception) {
            LOGGER.warn("Failed to replay latest note: roomId={}, userId={}", roomId, userId, exception);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }

        long roomId = sessionAttribute(session, ROOM_ID);
        long userId = sessionAttribute(session, USER_ID);
        String roomCode = String.valueOf(session.getAttributes().getOrDefault(ROOM_CODE, ""));

        try {
            JsonNode json = objectMapper.readTree(payload);
            String rejection = validate(json, roomId, userId, roomCode);
            if (rejection != null) {
                LOGGER.warn("Rejected WebSocket message from session={}: {}", session.getId(), rejection);
                return;
            }

            String text = json.path(TEXT).asText("").trim();
            String username = String.valueOf(session.getAttributes().get(USERNAME));

            RoomMessageService.PublishedMessage published =
                    roomMessageService.publish(roomId, roomCode, userId, username, text);
            LOGGER.info("Stored latest note: roomId={}, senderUserId={}, chars={}",
                    roomId, userId, text.length());

            published.recipientUserId().ifPresentOrElse(
                    recipientUserId -> deliver(roomId, recipientUserId, published.message()),
                    () -> LOGGER.info("No peer to deliver to yet: roomId={}, senderUserId={}", roomId, userId));
        } catch (Exception exception) {
            // Never let a bad frame or a database hiccup tear down a live sticker session.
            LOGGER.error("Failed to handle WebSocket message from session=" + session.getId(), exception);
        }
    }

    /** @return a human readable reason when the frame must be dropped, or {@code null} when it is valid. */
    private String validate(JsonNode json, long roomId, long userId, String roomCode) {
        if (!MESSAGE_TYPE.equals(json.path(TYPE).asText())) {
            return "unsupported type";
        }
        if (roomId < 0 || userId < 0) {
            return "session has no room identity";
        }
        if (!roomCode.equals(json.path(ROOM_CODE).asText("").trim())) {
            return "room code mismatch";
        }
        if (json.path(ROOM_ID).asLong(-1) != roomId) {
            return "room id mismatch";
        }
        if (json.path(USER_ID).asLong(-1) != userId) {
            return "user id mismatch";
        }

        String text = json.path(TEXT).asText("").trim();
        if (text.isBlank()) {
            return "blank text";
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return "text longer than " + MAX_TEXT_LENGTH + " characters";
        }
        return null;
    }

    /** Pushes the note to the other member's open sessions only, never back to the sender. */
    private void deliver(long roomId, long recipientUserId, RoomMessage message) {
        int delivered = 0;
        for (WebSocketSession peer : sessionsByRoom.getOrDefault(roomId, Set.of())) {
            if (peer.isOpen() && sessionAttribute(peer, USER_ID) == recipientUserId) {
                try {
                    sendMessage(peer, message);
                    delivered++;
                } catch (IOException exception) {
                    LOGGER.warn("Failed to deliver note to session=" + peer.getId(), exception);
                }
            }
        }
        LOGGER.info("Delivery finished: roomId={}, recipientUserId={}, sessionsDelivered={}",
                roomId, recipientUserId, delivered);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object roomIdValue = session.getAttributes().get(ROOM_ID);
        if (roomIdValue instanceof Long roomId) {
            Set<WebSocketSession> roomSessions = sessionsByRoom.get(roomId);
            if (roomSessions != null) {
                roomSessions.remove(session);
                if (roomSessions.isEmpty()) {
                    sessionsByRoom.remove(roomId, roomSessions);
                }
            }
        }
        LOGGER.info("WebSocket closed: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        LOGGER.warn("WebSocket transport error: session=" + session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Map<String, String> extractQuery(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return Map.of();
        }

        Map<String, String> values = new ConcurrentHashMap<>();
        for (String part : session.getUri().getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) {
                values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private long sessionAttribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof Long id ? id : -1;
    }

    private synchronized void sendMessage(WebSocketSession session, RoomMessage message) throws IOException {
        Map<String, Object> payload = Map.of(
                "type", MESSAGE_TYPE,
                "roomCode", message.roomCode(),
                "roomId", message.roomId(),
                "userId", message.userId(),
                "username", message.username(),
                "text", message.text());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
