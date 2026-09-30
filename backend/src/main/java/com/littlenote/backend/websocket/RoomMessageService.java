package com.littlenote.backend.websocket;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RoomMessageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomMessageService.class);

    private final RoomMessageRepository repository;

    public RoomMessageService(RoomMessageRepository repository) {
        this.repository = repository;
    }

    public Optional<RoomMessageRepository.Member> findMember(String roomCode, long roomId, long userId) {
        return repository.findMember(normalizeRoomCode(roomCode), roomId, userId);
    }

    /**
     * Stores the note as the room's new latest message and reports who must receive it.
     *
     * <p>The note is stored even when the second person has not joined yet. That stored note is
     * replayed to the peer the moment they connect, which is the only way the very first note of
     * a room is ever seen: the sender writes it before anybody is listening.
     */
    public PublishedMessage publish(long roomId, String roomCode, long senderUserId, String username, String text) {
        RoomMessageRepository.RoomMessage message = new RoomMessageRepository.RoomMessage(
                roomId,
                normalizeRoomCode(roomCode),
                senderUserId,
                normalizeUsername(username),
                normalizeText(text));
        repository.saveLatestMessage(roomId, message);

        // A sender must never be routed their own note, so the peer is looked up by "not me".
        Optional<Long> recipientUserId = repository.findOtherMemberId(roomId, senderUserId)
                .filter(recipient -> recipient.longValue() != senderUserId);
        if (recipientUserId.isEmpty()) {
            LOGGER.info("Stored latest note with no peer in the room yet: roomId={}, senderUserId={}",
                    roomId, senderUserId);
        }

        return new PublishedMessage(message, recipientUserId);
    }

    public Optional<RoomMessageRepository.RoomMessage> getLatestMessage(long roomId) {
        return repository.findLatestMessage(roomId);
    }

    private String normalizeRoomCode(String roomCode) {
        if (roomCode == null) {
            return "";
        }
        return roomCode.trim();
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    /**
     * @param message         the note that was just stored as the room's latest message
     * @param recipientUserId the other member of the room, or empty while nobody has joined yet
     */
    public record PublishedMessage(RoomMessageRepository.RoomMessage message, Optional<Long> recipientUserId) {
    }
}
