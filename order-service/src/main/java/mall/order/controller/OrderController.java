package mall.order.controller;

import lombok.RequiredArgsConstructor;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.dto.OrderHistoryResponseDto;
import mall.order.dto.ReserveResponseDto;
import mall.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponseDto> reserveOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody OrderCreateRequestDto request) {
        String accessToken = authHeader.substring(7);
        String orderId = orderService.reserveOrder(accessToken, request);
        return ResponseEntity.ok(new ReserveResponseDto(orderId));
    }

    @PostMapping("/cancel/{orderId}")
    public ResponseEntity<Void> cancelReservation(@PathVariable String orderId) {
        orderService.cancelReservation(orderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderHistoryResponseDto>> getMyOrders(
            @RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.substring(7);
        return ResponseEntity.ok(orderService.getMyOrders(accessToken));
    }

    @GetMapping("/health")
    public String status() {
        return "Order Service is working properly!";
    }
}
