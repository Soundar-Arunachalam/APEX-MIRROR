package com.bankingswitch.demo.npci.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class NpciDemoController {
    @PostMapping("/send")
    public Map<String, Object> sendRequest(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Request sent successfully to NPCI Simulator.");
        response.put("data", requestData);
        return response;
    }
}
