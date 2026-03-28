package mall.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.PaymentCompletedEvent;
import mall.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    public void handlePaymentCompleted(String rawPayload) {
        try {
            String json = rawPayload;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            PaymentCompletedEvent event = objectMapper.readValue(json, PaymentCompletedEvent.class);
            log.info("결제 완료 이벤트 수신: orderId={}", event.getOrderId());
            orderService.handlePaymentCompleted(event.getOrderId());
        } catch (Exception e) {
            log.error("payment-events 메시지 파싱 실패: {}", rawPayload, e);
        }
    }
}
