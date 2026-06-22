package com.bankingswitch.demo.cbs.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class CbsDemoController {
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ACTIVE");
        response.put("message", "CBS Simulator is running normally.");
        return response;
    }
}
