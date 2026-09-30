package com.littlenote.backend.testsupport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.littlenote.backend.model.Room;
import com.littlenote.backend.repository.RoomRepository;

/**
 * Stand-in for {@code JdbcRoomRepository} so room creation and joining can be tested without a
 * live PostgreSQL instance. Ids are handed out in the same monotonic way as SERIAL columns.
 */
public class InMemoryRoomRepository implements RoomRepository {

    private final Map<Long, String> usernamesById = new HashMap<>();
    private final Map<Long, Room> roomsById = new HashMap<>();
    private final Map<String, Long> roomIdByCode = new HashMap<>();
    private final Map<Long, Set<Long>> memberIdsByRoomId = new HashMap<>();
    private final Set<Long> roomsWithState = new HashSet<>();
    private final AtomicLong userSequence = new AtomicLong();
    private final AtomicLong roomSequence = new AtomicLong();

    @Override
    public long createUser(String username) {
        long userId = userSequence.incrementAndGet();
        usernamesById.put(userId, username);
        return userId;
    }

    @Override
    public long createRoom(String roomCode, long hostUserId) {
        long roomId = roomSequence.incrementAndGet();
        roomsById.put(roomId, new Room(roomId, roomCode, hostUserId));
        roomIdByCode.put(roomCode, roomId);
        return roomId;
    }

    @Override
    public void addMember(long roomId, long userId) {
        memberIdsByRoomId.computeIfAbsent(roomId, key -> new LinkedHashSet<>()).add(userId);
    }

    @Override
    public void createRoomState(long roomId) {
        roomsWithState.add(roomId);
    }

    @Override
    public Optional<Room> findRoomByCode(String roomCode) {
        Long roomId = roomIdByCode.get(roomCode);
        return roomId == null ? Optional.empty() : Optional.ofNullable(roomsById.get(roomId));
    }

    @Override
    public int countMembers(long roomId) {
        return memberIdsByRoomId.getOrDefault(roomId, Set.of()).size();
    }

    public boolean hasRoomState(long roomId) {
        return roomsWithState.contains(roomId);
    }

    public boolean isMember(long roomId, long userId) {
        return memberIdsByRoomId.getOrDefault(roomId, Set.of()).contains(userId);
    }

    public String usernameOf(long userId) {
        return usernamesById.get(userId);
    }
}
