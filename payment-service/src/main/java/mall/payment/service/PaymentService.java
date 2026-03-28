package mall.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.PaymentCompletedEvent;
import mall.payment.dto.PaymentConfirmRequestDto;
import mall.payment.entity.OutboxEvent;
import mall.payment.entity.Payment;
import mall.payment.entity.PaymentStatus;
import mall.payment.repository.OutboxRepository;
import mall.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${toss.secret-key}")
    private String secretKey;

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void confirmPayment(PaymentConfirmRequestDto request) {
        // 1. 토스 결제 승인 API 호출
        callTossConfirmApi(request.getPaymentKey(), request.getTossOrderId(), request.getAmount());

        // 2. Payment 저장
        Payment payment = Payment.builder()
                .orderId(request.getTossOrderId())
                .paymentKey(request.getPaymentKey())
                .amount(request.getAmount())
                .status(PaymentStatus.COMPLETED)
                .build();
        paymentRepository.save(payment);

        // 3. Outbox에 PaymentCompletedEvent 저장 (Debezium이 payment-events로 발행)
        try {
            String payload = objectMapper.writeValueAsString(
                    new PaymentCompletedEvent(request.getTossOrderId(), request.getPaymentKey(), request.getAmount()));
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType("payment-events")
                    .aggregateId(request.getTossOrderId())
                    .type("PaymentCompletedEvent")
                    .payload(payload)
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 직렬화 실패", e);
        }

        log.info("결제 승인 완료: orderId={}, amount={}", request.getTossOrderId(), request.getAmount());
    }

    @Transactional
    public void refundPayment(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다: " + orderId));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("이미 환불된 결제 무시: orderId={}", orderId);
            return;
        }

        callTossRefundApi(payment.getPaymentKey());
        payment.refund();
        log.info("환불 완료: orderId={}, paymentKey={}", orderId, payment.getPaymentKey());
    }

    private HttpHeaders buildTossHeaders() {
        String credentials = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + credentials);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void callTossConfirmApi(String paymentKey, String orderId, Long amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        try {
            restTemplate.postForEntity(
                    "https://api.tosspayments.com/v1/payments/confirm",
                    new HttpEntity<>(body, buildTossHeaders()),
                    String.class
            );
        } catch (HttpClientErrorException e) {
            log.error("토스 결제 승인 실패: {}", e.getResponseBodyAsString());
            throw new RuntimeException("결제 승인에 실패했습니다.");
        }
    }

    private void callTossRefundApi(String paymentKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", "재고 부족으로 인한 자동 환불");

        try {
            restTemplate.postForEntity(
                    "https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel",
                    new HttpEntity<>(body, buildTossHeaders()),
                    String.class
            );
        } catch (HttpClientErrorException e) {
            log.error("토스 환불 실패: paymentKey={}, {}", paymentKey, e.getResponseBodyAsString());
            throw new RuntimeException("환불 처리에 실패했습니다.");
        }
    }
}
