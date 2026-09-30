package com.littlenote.backend.repository;

import java.sql.PreparedStatement;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.littlenote.backend.model.Room;

@Repository
public class JdbcRoomRepository implements RoomRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long createUser(String username) {
        String sql = "INSERT INTO users (username) VALUES (?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
            ps.setString(1, username);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : -1L;
    }

    @Override
    public long createRoom(String roomCode, long hostUserId) {
        String sql = "INSERT INTO rooms (code, host_user_id) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
            ps.setString(1, roomCode);
            ps.setLong(2, hostUserId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : -1L;
    }

    @Override
    public void addMember(long roomId, long userId) {
        String sql = "INSERT INTO room_members (room_id, user_id) VALUES (?, ?)";
        jdbcTemplate.update(sql, roomId, userId);
    }

    @Override
    public void createRoomState(long roomId) {
        String sql = "INSERT INTO room_state (room_id, state_json) VALUES (?, ?::jsonb)";
        jdbcTemplate.update(sql, roomId, "{\"status\":\"waiting\"}");
    }

    @Override
    public Optional<Room> findRoomByCode(String roomCode) {
        String sql = "SELECT id, code, host_user_id FROM rooms WHERE code = ?";
        return jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(new Room(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("host_user_id"))) : Optional.empty(), roomCode);
    }

    @Override
    public int countMembers(long roomId) {
        String sql = "SELECT COUNT(*) FROM room_members WHERE room_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, roomId);
    }
}
