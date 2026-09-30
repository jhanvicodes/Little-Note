package com.littlenote.backend.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.littlenote.backend.model.CreateRoomRequest;
import com.littlenote.backend.model.JoinRoomRequest;
import com.littlenote.backend.model.Room;
import com.littlenote.backend.model.RoomResponse;
import com.littlenote.backend.repository.RoomRepository;

@Service
public class RoomService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private final RoomRepository roomRepository;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        String username = normalizeUsername(request.username());
        long userId = roomRepository.createUser(username);
        String roomCode = generateUniqueRoomCode();
        long roomId = roomRepository.createRoom(roomCode, userId);
        roomRepository.addMember(roomId, userId);
        roomRepository.createRoomState(roomId);

        return new RoomResponse(roomCode, username, userId, roomId);
    }

    @Transactional
    public RoomResponse joinRoom(JoinRoomRequest request) {
        String username = normalizeUsername(request.username());
        String roomCode = normalizeRoomCode(request.roomCode());

        Optional<Room> roomOpt = roomRepository.findRoomByCode(roomCode);
        if (roomOpt.isEmpty()) {
            throw new IllegalArgumentException("Room not found");
        }

        Room room = roomOpt.get();
        int currentMembers = roomRepository.countMembers(room.id());
        if (currentMembers >= 2) {
            throw new IllegalStateException("Room is already full");
        }

        long userId = roomRepository.createUser(username);
        roomRepository.addMember(room.id(), userId);

        return new RoomResponse(room.code(), username, userId, room.id());
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        String normalized = username.trim();
        if (normalized.length() > 18) {
            normalized = normalized.substring(0, 18);
        }

        return normalized;
    }

    private String normalizeRoomCode(String roomCode) {
        if (roomCode == null || roomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Room code is required");
        }

        return roomCode.trim().toUpperCase(Locale.ROOT);
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                code.append(UPPER.charAt(random.nextInt(UPPER.length())));
            }
            String roomCode = code.toString();
            if (roomRepository.findRoomByCode(roomCode).isEmpty()) {
                return roomCode;
            }
        }

        throw new IllegalStateException("Unable to generate a unique room code");
    }
}
