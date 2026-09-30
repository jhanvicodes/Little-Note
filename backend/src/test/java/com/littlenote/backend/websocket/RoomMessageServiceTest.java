package com.littlenote.backend.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.littlenote.backend.testsupport.InMemoryRoomMessageRepository;
import com.littlenote.backend.websocket.RoomMessageRepository.RoomMessage;

class RoomMessageServiceTest {

    private static final long ROOM_ID = 7L;
    private static final String ROOM_CODE = "ROOM01";
    private static final long ALICE = 1L;
    private static final long BOB = 2L;

    private InMemoryRoomMessageRepository repository;
    private RoomMessageService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoomMessageRepository();
        repository.registerRoom(ROOM_ID, ROOM_CODE);
        repository.addMember(ROOM_ID, ALICE, "alice");
        repository.addMember(ROOM_ID, BOB, "bob");
        service = new RoomMessageService(repository);
    }

    @Test
    void publishStoresTheNoteAsTheRoomsLatestMessage() {
        service.publish(ROOM_ID, ROOM_CODE, ALICE, "alice", "hiii");

        RoomMessage stored = service.getLatestMessage(ROOM_ID).orElseThrow();
        assertEquals(ROOM_CODE, stored.roomCode());
        assertEquals(ALICE, stored.userId());
        assertEquals("alice", stored.username());
        assertEquals("hiii", stored.text());
    }

    @Test
    void publishTargetsOnlyTheOtherMemberOfTheRoom() {
        RoomMessageService.PublishedMessage published =
                service.publish(ROOM_ID, ROOM_CODE, ALICE, "alice", "hiii");

        assertEquals(BOB, published.recipientUserId().orElseThrow());
    }

    @Test
    void publishNeverTargetsTheSender() {
        RoomMessageService.PublishedMessage published =
                service.publish(ROOM_ID, ROOM_CODE, BOB, "bob", "heyyy");

        assertTrue(published.recipientUserId().isPresent());
        assertFalse(published.recipientUserId().get() == BOB);
    }

    @Test
    void publishStillStoresTheNoteWhenNoPeerHasJoinedYet() {
        InMemoryRoomMessageRepository lonelyRoom = new InMemoryRoomMessageRepository();
        lonelyRoom.registerRoom(ROOM_ID, ROOM_CODE);
        lonelyRoom.addMember(ROOM_ID, ALICE, "alice");
        RoomMessageService lonelyService = new RoomMessageService(lonelyRoom);

        RoomMessageService.PublishedMessage published =
                lonelyService.publish(ROOM_ID, ROOM_CODE, ALICE, "alice", "hiii");

        // Nobody is listening yet, so there is no recipient, but the note must still be stored:
        // it is replayed when the second person connects.
        assertTrue(published.recipientUserId().isEmpty());
        assertEquals("hiii", lonelyService.getLatestMessage(ROOM_ID).orElseThrow().text());
    }

    @Test
    void latestMessageReplacesThePreviousNoteInsteadOfAppending() {
        service.publish(ROOM_ID, ROOM_CODE, ALICE, "alice", "hiii");
        service.publish(ROOM_ID, ROOM_CODE, BOB, "bob", "heyyy");

        assertEquals(1, repository.storedMessageCount(ROOM_ID));
        RoomMessage stored = service.getLatestMessage(ROOM_ID).orElseThrow();
        assertEquals("heyyy", stored.text());
        assertEquals(BOB, stored.userId());
    }

    @Test
    void getLatestMessageIsEmptyBeforeAnythingIsSent() {
        assertTrue(service.getLatestMessage(ROOM_ID).isEmpty());
    }

    @Test
    void findMemberAcceptsBothRoomMembers() {
        assertTrue(service.findMember(ROOM_CODE, ROOM_ID, ALICE).isPresent());
        assertTrue(service.findMember(ROOM_CODE, ROOM_ID, BOB).isPresent());
        assertEquals("bob", service.findMember(ROOM_CODE, ROOM_ID, BOB).orElseThrow().username());
    }

    @Test
    void findMemberRejectsAUserOutsideTheRoom() {
        assertTrue(service.findMember(ROOM_CODE, ROOM_ID, 99L).isEmpty());
    }

    @Test
    void findMemberRejectsAMismatchedRoomCode() {
        assertTrue(service.findMember("OTHERC", ROOM_ID, ALICE).isEmpty());
    }
}
