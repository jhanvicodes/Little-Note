package com.littlenote.backend.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, String> testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Map.of("database", "connected");
        } catch (Exception ex) {
            logger.warn("Database connection test failed", ex);
            return Map.of("database", "disconnected");
        }
    }
}
