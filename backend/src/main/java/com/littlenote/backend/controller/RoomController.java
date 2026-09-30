package com.littlenote.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.littlenote.backend.model.CreateRoomRequest;
import com.littlenote.backend.model.JoinRoomRequest;
import com.littlenote.backend.model.RoomResponse;
import com.littlenote.backend.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/create")
    public ResponseEntity<Object> createRoom(@RequestBody CreateRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.createRoom(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/join")
    public ResponseEntity<Object> joinRoom(@RequestBody JoinRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.joinRoom(request));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage();
            if ("Room not found".equals(message)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", message));
            }
            return ResponseEntity.badRequest().body(Map.of("error", message));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }
}
