package com.hpe.upi.npci;
import com.hpe.upi.npci.service.NpciRoutingService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/npci")
public class NpciController {
    private final NpciRoutingService svc;
    public NpciController(NpciRoutingService svc) { this.svc = svc; }
    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status","UP","service","NPCI-Router"); }
    @GetMapping("/inflight")
    public Map<String,Object> inflight() { return Map.of("inflight", svc.getInFlight(), "history", svc.getHistory()); }
}
