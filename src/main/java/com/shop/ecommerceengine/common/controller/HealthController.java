package com.shop.ecommerceengine.common.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check controller for verifying application status.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Health check endpoints")
public class HealthController {

    @GetMapping
    @Operation(summary = "Health check", description = "Returns the current health status of the application")
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> healthData = Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "ecommerce-engine"
        );
        return ApiResponse.success(healthData, "Service is healthy");
    }
}
