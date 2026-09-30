package com.littlenote.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.littlenote.backend.service.DatabaseHealthService;

@RestController
@RequestMapping("/api")
public class DatabaseController {

    private final DatabaseHealthService databaseHealthService;

    public DatabaseController(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/db-test")
    public Map<String, String> dbTest() {
        return databaseHealthService.testConnection();
    }
}
