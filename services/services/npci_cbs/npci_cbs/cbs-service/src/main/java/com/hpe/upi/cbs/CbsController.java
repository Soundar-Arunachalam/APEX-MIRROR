package com.hpe.upi.cbs;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/cbs")
public class CbsController {
    @GetMapping("/health")
    public Map<String,String> health() {
        return Map.of("status","UP","service","CBS-Service","databases","debit:5433,credit:5434");
    }
}
