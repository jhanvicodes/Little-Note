package com.littlenote.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.littlenote.backend.model.CreateRoomRequest;
import com.littlenote.backend.model.JoinRoomRequest;
import com.littlenote.backend.model.RoomResponse;
import com.littlenote.backend.testsupport.InMemoryRoomRepository;

class RoomServiceTest {

    private InMemoryRoomRepository repository;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoomRepository();
        roomService = new RoomService(repository);
    }

    @Test
    void successfulRoomCreation() {
        RoomResponse response = roomService.createRoom(new CreateRoomRequest("creator"));

        assertNotNull(response.roomCode());
        assertEquals(6, response.roomCode().length());
        assertEquals("creator", response.username());
        assertNotNull(response.userId());
        assertNotNull(response.roomId());
        assertTrue(repository.isMember(response.roomId(), response.userId()));
        assertTrue(repository.hasRoomState(response.roomId()));
    }

    @Test
    void successfulRoomJoin() {
        RoomResponse created = roomService.createRoom(new CreateRoomRequest("creator"));
        RoomResponse joined = roomService.joinRoom(new JoinRoomRequest("joiner", created.roomCode()));

        assertEquals(created.roomCode(), joined.roomCode());
        assertEquals(created.roomId(), joined.roomId());
        assertEquals("joiner", joined.username());
        assertNotEquals(created.userId(), joined.userId());
        assertTrue(repository.isMember(created.roomId(), joined.userId()));
    }

    @Test
    void joinAcceptsALowercaseRoomCode() {
        RoomResponse created = roomService.createRoom(new CreateRoomRequest("creator"));
        RoomResponse joined = roomService.joinRoom(
                new JoinRoomRequest("joiner", created.roomCode().toLowerCase()));

        assertEquals(created.roomId(), joined.roomId());
    }

    @Test
    void repeatedCreateRoomWithSameUsernameIsAllowed() {
        RoomResponse first = roomService.createRoom(new CreateRoomRequest("same-user"));
        RoomResponse second = roomService.createRoom(new CreateRoomRequest("same-user"));

        assertEquals("same-user", first.username());
        assertEquals("same-user", second.username());
        assertNotEquals(first.roomCode(), second.roomCode());
        assertNotEquals(first.roomId(), second.roomId());
    }

    @Test
    void creationWithoutAUsernameIsRejected() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> roomService.createRoom(new CreateRoomRequest("   ")));

        assertEquals("Username is required", exception.getMessage());
    }

    @Test
    void invalidRoomCode() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> roomService.joinRoom(new JoinRoomRequest("someone", "NOPE99")));

        assertEquals("Room not found", exception.getMessage());
    }

    @Test
    void thirdUserCannotJoinFullRoom() {
        RoomResponse room = roomService.createRoom(new CreateRoomRequest("alpha"));
        roomService.joinRoom(new JoinRoomRequest("beta", room.roomCode()));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> roomService.joinRoom(new JoinRoomRequest("gamma", room.roomCode())));

        assertEquals("Room is already full", exception.getMessage());
    }
}
