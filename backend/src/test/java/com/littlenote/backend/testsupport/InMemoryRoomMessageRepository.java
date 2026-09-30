package com.littlenote.backend.testsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.littlenote.backend.websocket.RoomMessageRepository;

/**
 * Stand-in for {@code JdbcRoomMessageRepository} so the messaging rules can be tested without a
 * live PostgreSQL instance. It mirrors the SQL semantics: one latest message per room, and the
 * peer of a user is the other member row of that room.
 */
public class InMemoryRoomMessageRepository implements RoomMessageRepository {

    private final Map<Long, String> roomCodesById = new HashMap<>();
    private final Map<Long, List<Member>> membersByRoomId = new HashMap<>();
    private final Map<Long, RoomMessage> latestMessageByRoomId = new HashMap<>();

    public void registerRoom(long roomId, String roomCode) {
        roomCodesById.put(roomId, roomCode);
    }

    public void addMember(long roomId, long userId, String username) {
        membersByRoomId.computeIfAbsent(roomId, key -> new ArrayList<>()).add(new Member(userId, username));
    }

    @Override
    public Optional<Member> findMember(String roomCode, long roomId, long userId) {
        if (!roomCode.equals(roomCodesById.get(roomId))) {
            return Optional.empty();
        }
        return membersByRoomId.getOrDefault(roomId, List.of()).stream()
                .filter(member -> member.userId() == userId)
                .findFirst();
    }

    @Override
    public Optional<Long> findOtherMemberId(long roomId, long userId) {
        return membersByRoomId.getOrDefault(roomId, List.of()).stream()
                .map(Member::userId)
                .filter(memberId -> memberId != userId)
                .findFirst();
    }

    @Override
    public void saveLatestMessage(long roomId, RoomMessage message) {
        latestMessageByRoomId.put(roomId, message);
    }

    @Override
    public Optional<RoomMessage> findLatestMessage(long roomId) {
        return Optional.ofNullable(latestMessageByRoomId.get(roomId));
    }

    public int storedMessageCount(long roomId) {
        return latestMessageByRoomId.containsKey(roomId) ? 1 : 0;
    }
}
