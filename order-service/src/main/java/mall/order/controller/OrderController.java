package mall.order.controller;

import lombok.RequiredArgsConstructor;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public ResponseEntity<Void> createOrder(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody OrderCreateRequestDto orderCreateRequestDto) {
        String accessToken = authHeader.substring(7);
        orderService.createOrder(accessToken, orderCreateRequestDto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/create-limited")
    public ResponseEntity<Void> createLimitedOrder(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody OrderCreateRequestDto orderCreateRequestDto) {
        String accessToken = authHeader.substring(7);
        orderService.createLimitedOrder(accessToken, orderCreateRequestDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public String status() {
        return "Order Service is working properly!";
    }
}
