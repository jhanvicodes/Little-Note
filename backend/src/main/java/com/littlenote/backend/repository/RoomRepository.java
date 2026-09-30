package com.littlenote.backend.repository;

import java.util.Optional;

import com.littlenote.backend.model.Room;

public interface RoomRepository {
    long createUser(String username);

    long createRoom(String roomCode, long hostUserId);

    void addMember(long roomId, long userId);

    void createRoomState(long roomId);

    Optional<Room> findRoomByCode(String roomCode);

    int countMembers(long roomId);
}
