package mall.payment.controller;

import lombok.RequiredArgsConstructor;
import mall.payment.dto.PaymentConfirmRequestDto;
import mall.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmPayment(@RequestBody PaymentConfirmRequestDto request) {
        paymentService.confirmPayment(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public String health() {
        return "Payment Service is working properly!";
    }
}
