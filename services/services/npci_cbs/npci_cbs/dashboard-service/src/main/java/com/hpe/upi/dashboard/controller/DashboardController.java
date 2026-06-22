package com.hpe.upi.dashboard.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Controller
public class DashboardController {
    @GetMapping("/") public String index() { return "forward:/index.html"; }
    @GetMapping("/api/health") @ResponseBody
    public Map<String,String> health() { return Map.of("status","UP","service","Dashboard"); }
}
