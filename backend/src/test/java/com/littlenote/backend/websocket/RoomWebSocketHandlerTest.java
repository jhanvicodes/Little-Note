package com.littlenote.backend.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.littlenote.backend.testsupport.InMemoryRoomMessageRepository;
import com.littlenote.backend.testsupport.RecordingWebSocketSession;

/**
 * Drives the handler directly so the routing rules of the sticker are pinned down:
 * a note reaches the other person, never its author, and only the latest note survives.
 */
class RoomWebSocketHandlerTest {

    private static final long ROOM_ID = 42L;
    private static final String ROOM_CODE = "PAIR01";
    private static final long PERSON_ONE = 11L;
    private static final long PERSON_TWO = 22L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InMemoryRoomMessageRepository repository;
    private RoomWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoomMessageRepository();
        repository.registerRoom(ROOM_ID, ROOM_CODE);
        repository.addMember(ROOM_ID, PERSON_ONE, "one");
        repository.addMember(ROOM_ID, PERSON_TWO, "two");
        handler = new RoomWebSocketHandler(new RoomMessageService(repository), objectMapper);
    }

    @Test
    void noteFromPersonOneReachesPersonTwoAndNotTheSender() throws Exception {
        RecordingWebSocketSession personOne = openSession(handler, "session-one", PERSON_ONE);
        RecordingWebSocketSession personTwo = openSession(handler, "session-two", PERSON_TWO);

        handler.handleTextMessage(personOne, frame(PERSON_ONE, ROOM_CODE, "hiii"));

        assertEquals(List.of("hiii"), textsSentTo(personTwo));
        assertTrue(textsSentTo(personOne).isEmpty(), "the sender must never be shown their own note");
    }

    @Test
    void replyFromPersonTwoReachesPersonOneAndNotTheSender() throws Exception {
        RecordingWebSocketSession personOne = openSession(handler, "session-one", PERSON_ONE);
        RecordingWebSocketSession personTwo = openSession(handler, "session-two", PERSON_TWO);

        handler.handleTextMessage(personTwo, frame(PERSON_TWO, ROOM_CODE, "heyyy"));

        assertEquals(List.of("heyyy"), textsSentTo(personOne));
        assertTrue(textsSentTo(personTwo).isEmpty(), "the sender must never be shown their own note");
    }

    @Test
    void noteWrittenBeforeThePeerJoinedIsReplayedOnConnect() throws Exception {
        InMemoryRoomMessageRepository lonelyRoom = new InMemoryRoomMessageRepository();
        lonelyRoom.registerRoom(ROOM_ID, ROOM_CODE);
        lonelyRoom.addMember(ROOM_ID, PERSON_ONE, "one");
        RoomWebSocketHandler lonelyHandler =
                new RoomWebSocketHandler(new RoomMessageService(lonelyRoom), objectMapper);

        RecordingWebSocketSession personOne = openSession(lonelyHandler, "session-one", PERSON_ONE);
        lonelyHandler.handleTextMessage(personOne, frame(PERSON_ONE, ROOM_CODE, "hiii"));
        assertTrue(textsSentTo(personOne).isEmpty(), "the sender must never be shown their own note");

        // The second person joins afterwards and must still be shown the note.
        lonelyRoom.addMember(ROOM_ID, PERSON_TWO, "two");
        RecordingWebSocketSession personTwo = openSession(lonelyHandler, "session-two", PERSON_TWO);

        assertEquals(List.of("hiii"), textsSentTo(personTwo));
    }

    @Test
    void onlyTheLatestNoteSurvivesSoThereIsNoHistory() throws Exception {
        RecordingWebSocketSession personOne = openSession(handler, "session-one", PERSON_ONE);

        handler.handleTextMessage(personOne, frame(PERSON_ONE, ROOM_CODE, "first"));
        handler.handleTextMessage(personOne, frame(PERSON_ONE, ROOM_CODE, "second"));

        RecordingWebSocketSession personTwo = openSession(handler, "session-two", PERSON_TWO);

        assertEquals(1, repository.storedMessageCount(ROOM_ID));
        assertEquals(List.of("second"), textsSentTo(personTwo),
                "a joiner sees exactly one note, the latest, not a transcript");
    }

    @Test
    void authorIsNeverShownTheirOwnStoredNoteOnReconnect() throws Exception {
        openSession(handler, "session-one", PERSON_ONE);
        RecordingWebSocketSession personTwo = openSession(handler, "session-two", PERSON_TWO);

        handler.handleTextMessage(personTwo, frame(PERSON_TWO, ROOM_CODE, "heyyy"));

        RecordingWebSocketSession personTwoAgain = openSession(handler, "session-two-again", PERSON_TWO);

        assertTrue(textsSentTo(personTwoAgain).isEmpty(), "the author must not get their own note replayed");
    }

    @Test
    void connectionFromOutsideTheRoomIsRejected() throws Exception {
        RecordingWebSocketSession stranger =
                RecordingWebSocketSession.forRoom("session-stranger", ROOM_CODE, ROOM_ID, 999L);

        handler.afterConnectionEstablished(stranger);

        assertTrue(stranger.wasClosed(), "a non-member must not hold a session");
        assertFalse(stranger.isOpen());
    }

    @Test
    void frameWithAMismatchedRoomCodeIsDropped() throws Exception {
        RecordingWebSocketSession personOne = openSession(handler, "session-one", PERSON_ONE);

        handler.handleTextMessage(personOne, frame(PERSON_ONE, "WRONG1", "hiii"));

        assertTrue(repository.findLatestMessage(ROOM_ID).isEmpty());
        assertTrue(textsSentTo(personOne).isEmpty());
        assertEquals("hiii", objectMapper.readTree(frame(PERSON_ONE, "WRONG1", "hiii").getPayload())
                .path("text").asText(), "the frame itself is well formed, only the room code is wrong");
    }

    private RecordingWebSocketSession openSession(RoomWebSocketHandler target, String sessionId, long userId)
            throws Exception {
        RecordingWebSocketSession session =
                RecordingWebSocketSession.forRoom(sessionId, ROOM_CODE, ROOM_ID, userId);
        target.afterConnectionEstablished(session);
        return session;
    }

    private TextMessage frame(long userId, String roomCode, String text) throws Exception {
        return new TextMessage(objectMapper.writeValueAsString(Map.<String, Object>of(
                "type", "message",
                "roomCode", roomCode,
                "roomId", ROOM_ID,
                "userId", userId,
                "text", text)));
    }

    private List<String> textsSentTo(RecordingWebSocketSession session) throws Exception {
        return session.sentPayloads().stream()
                .map(this::readText)
                .toList();
    }

    private String readText(String payload) {
        try {
            return objectMapper.readTree(payload).path("text").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable frame: " + payload, exception);
        }
    }
}
