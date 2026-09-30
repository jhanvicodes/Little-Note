package com.littlenote.backend.websocket;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.littlenote.backend.websocket.RoomMessageRepository.Member;
import com.littlenote.backend.websocket.RoomMessageRepository.RoomMessage;

@Repository
public class JdbcRoomMessageRepository implements RoomMessageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRoomMessageRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Member> findMember(String roomCode, long roomId, long userId) {
        String sql = "SELECT u.id, u.username FROM rooms r "
                + "JOIN room_members rm ON rm.room_id = r.id "
                + "JOIN users u ON u.id = rm.user_id "
                + "WHERE r.code = ? AND r.id = ? AND u.id = ?";
        return jdbcTemplate.query(sql, rs -> rs.next()
                ? Optional.of(new Member(rs.getLong("id"), rs.getString("username")))
                : Optional.empty(), roomCode, roomId, userId);
    }

    @Override
    public Optional<Long> findOtherMemberId(long roomId, long userId) {
        String sql = "SELECT user_id FROM room_members WHERE room_id = ? AND user_id <> ? LIMIT 1";
        return jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong("user_id")) : Optional.empty(),
                roomId, userId);
    }

    @Override
    public void saveLatestMessage(long roomId, RoomMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode latest room message", exception);
        }

        String sql = "UPDATE room_state SET state_json = jsonb_set(state_json, '{latestMessage}', ?::jsonb, true), "
                + "updated_at = CURRENT_TIMESTAMP WHERE room_id = ?";
        int updated = jdbcTemplate.update(sql, json, roomId);
        if (updated != 1) {
            throw new IllegalStateException("Room state not found for room " + roomId);
        }
    }

    @Override
    public Optional<RoomMessage> findLatestMessage(long roomId) {
        String sql = "SELECT state_json -> 'latestMessage' AS latest_message FROM room_state WHERE room_id = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            String json = rs.getString("latest_message");
            if (json == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(json, RoomMessage.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to decode latest room message", exception);
            }
        }, roomId);
    }
}
