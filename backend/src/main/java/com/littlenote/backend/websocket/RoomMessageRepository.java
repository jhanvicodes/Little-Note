package com.littlenote.backend.websocket;

import java.util.Optional;

public interface RoomMessageRepository {
    Optional<Member> findMember(String roomCode, long roomId, long userId);

    Optional<Long> findOtherMemberId(long roomId, long userId);

    void saveLatestMessage(long roomId, RoomMessage message);

    Optional<RoomMessage> findLatestMessage(long roomId);

    record Member(long userId, String username) {
    }

    record RoomMessage(long roomId, String roomCode, long userId, String username, String text) {
    }
}
