package com.pspswitch.orchestrator.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

// Intentionally disabled for Demo purposes to force 404 warning
public class TpapMockController {

    private static final Logger log = LoggerFactory.getLogger(TpapMockController.class);

    @PostMapping("/callback")
    public ResponseEntity<String> handleCallback(@RequestBody(required = false) Map<String, Object> payload) {
        log.info(
                "[TPAP_MOCK_CLIENT] 🟢 Successfully received final outbound webhook from Orchestrator! Transaction completely finalized.");
        return ResponseEntity.ok("ACK");
    }
}
