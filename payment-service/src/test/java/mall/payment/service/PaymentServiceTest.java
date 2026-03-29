package mall.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import mall.payment.dto.PaymentConfirmRequestDto;
import mall.payment.entity.OutboxEvent;
import mall.payment.entity.Payment;
import mall.payment.entity.PaymentStatus;
import mall.payment.repository.OutboxRepository;
import mall.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock RestTemplate restTemplate;

    @InjectMocks
    PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        // @Value 필드 직접 주입
        var secretField = PaymentService.class.getDeclaredField("secretKey");
        secretField.setAccessible(true);
        secretField.set(paymentService, "test_sk_dummy");

        var mapperField = PaymentService.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(paymentService, new ObjectMapper());
    }

    // ──────────────────────────────────────────────
    // confirmPayment
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("결제 승인 성공 - Payment 저장 + outbox PaymentCompletedEvent 저장")
    void confirmPayment_성공() {
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("{}"));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentConfirmRequestDto request = new PaymentConfirmRequestDto(
                "pay-key-1", "ord-1", 50000L);

        paymentService.confirmPayment(request);

        // Payment 저장 검증
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        then(paymentRepository).should().save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo("ord-1");
        assertThat(paymentCaptor.getValue().getPaymentKey()).isEqualTo("pay-key-1");
        assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(50000L);
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Outbox 저장 검증
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getAggregateType()).isEqualTo("payment-events");
        assertThat(outboxCaptor.getValue().getType()).isEqualTo("PaymentCompletedEvent");
        assertThat(outboxCaptor.getValue().getPayload()).contains("ord-1").contains("pay-key-1");
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 오류 시 Payment/Outbox 저장 안 함")
    void confirmPayment_Toss실패() {
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willThrow(HttpClientErrorException.BadRequest.create(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bad Request", null, null, null));

        PaymentConfirmRequestDto request = new PaymentConfirmRequestDto(
                "invalid-key", "ord-1", 50000L);

        assertThatThrownBy(() -> paymentService.confirmPayment(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("결제 승인에 실패");

        then(paymentRepository).should(never()).save(any());
        then(outboxRepository).should(never()).save(any());
    }

    // ──────────────────────────────────────────────
    // refundPayment
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("환불 성공 - Toss cancel API 호출 + Payment 상태 REFUNDED")
    void refundPayment_성공() {
        Payment payment = Payment.builder()
                .orderId("ord-1").paymentKey("pay-key-1")
                .amount(50000L).status(PaymentStatus.COMPLETED).build();
        given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(payment));
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("{}"));

        paymentService.refundPayment("ord-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        then(restTemplate).should().postForEntity(
                contains("pay-key-1/cancel"), any(), eq(String.class));
    }

    @Test
    @DisplayName("환불 실패 - 결제 정보 없음")
    void refundPayment_결제정보없음() {
        given(paymentRepository.findByOrderId("ord-999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment("ord-999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("결제 정보를 찾을 수 없습니다");

        then(restTemplate).should(never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("refund skipped - already REFUNDED (idempotent)")
    void refundPayment_alreadyRefunded_skipped() {
        Payment payment = Payment.builder()
                .orderId("ord-1").paymentKey("pay-key-1")
                .amount(50000L).status(PaymentStatus.REFUNDED).build();
        given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(payment));

        paymentService.refundPayment("ord-1");

        then(restTemplate).should(never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("환불 실패 - Toss cancel API 오류 시 Payment 상태 유지")
    void refundPayment_Toss실패() {
        Payment payment = Payment.builder()
                .orderId("ord-1").paymentKey("pay-key-1")
                .amount(50000L).status(PaymentStatus.COMPLETED).build();
        given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(payment));
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willThrow(HttpClientErrorException.BadRequest.create(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bad Request", null, null, null));

        assertThatThrownBy(() -> paymentService.refundPayment("ord-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("환불 처리에 실패");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED); // 상태 변경 안 됨
    }
}
