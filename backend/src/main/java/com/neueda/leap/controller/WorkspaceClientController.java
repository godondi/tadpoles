package com.neueda.leap.controller;
import com.neueda.leap.dto.WorkspaceDtos.*;
import com.neueda.leap.dto.InstrumentResponseDto;
import com.neueda.leap.service.*;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CLIENT')")
public class WorkspaceClientController {
    private final WorkspaceReadService reads;
    private final AppUserService users;
    public WorkspaceClientController(WorkspaceReadService reads,AppUserService users) { this.reads=reads; this.users=users; }
    private int clientId(Jwt jwt) {
        Integer id=SecurityRoleSupport.resolveCurrentUser(jwt,users).getClientId();
        if (id==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Complete your client onboarding first");
        return id;
    }
    @GetMapping("/workspace/client/portfolio") public Portfolio portfolio(@AuthenticationPrincipal Jwt jwt) { return reads.portfolio(clientId(jwt)); }
    @GetMapping("/orders") public Page<Order> orders(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String status,@RequestParam(required=false) String side,
            @RequestParam(required=false) Integer instrumentId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        return reads.orders(clientId(jwt),page,pageSize,status,side,instrumentId,from,to);
    }
    @GetMapping("/orders/{id}") public OrderDetail order(@AuthenticationPrincipal Jwt jwt,@PathVariable int id) { return reads.order(clientId(jwt),id); }
    @GetMapping("/workspace/client/instruments") public Page<InstrumentResponseDto> instruments(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(defaultValue="") String search,@RequestParam(required=false) String assetClass) { return reads.instruments(page,pageSize,search,assetClass); }
    @GetMapping("/workspace/client/trading-availability") public TradingAvailability availability() {
        return new TradingAvailability(false,"Trading is not available yet. Approved instruments, account currencies and execution rules must be confirmed before orders can be submitted.");
    }
    @PostMapping("/orders") public void submit() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trading is not enabled. No order has been accepted or recorded.");
    }
}
