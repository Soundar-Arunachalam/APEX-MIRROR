package com.pspswitch.tpapingress.controller;

import com.pspswitch.tpapingress.dto.response.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /health — service liveness check.
 * Auth filter skips this endpoint.
 */
@RestController
@RequestMapping("/tpap/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        // v1: simple liveness check, always returns UP
        HealthResponse response = HealthResponse.builder()
                .status("UP")
                .components(HealthResponse.Components.builder()
                        .kafka("UP")
                        .redis("UP")
                        .postgres("UP")
                        .build())
                .build();
        return ResponseEntity.ok(response);
    }
}
