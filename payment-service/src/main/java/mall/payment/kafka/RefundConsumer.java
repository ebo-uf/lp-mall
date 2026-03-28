package mall.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.PaymentRefundEvent;
import mall.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefundConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-refund", groupId = "payment-service-group")
    public void handleRefund(String rawPayload) {
        try {
            String json = rawPayload;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            PaymentRefundEvent event = objectMapper.readValue(json, PaymentRefundEvent.class);
            log.info("환불 요청 수신: orderId={}", event.getOrderId());
            paymentService.refundPayment(event.getOrderId());
        } catch (Exception e) {
            log.error("payment-refund 메시지 파싱 실패: {}", rawPayload, e);
        }
    }
}
