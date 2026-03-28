package mall.product.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.OrderCreatedEvent;
import mall.product.service.OrderEventService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderEventService orderEventService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", groupId = "product-service-group")
    public void handleOrder(String rawPayload) {
        try {
            String json = rawPayload;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            OrderCreatedEvent event = objectMapper.readValue(json, OrderCreatedEvent.class);
            log.info("주문 이벤트 수신: orderId={}", event.getOrderId());
            try {
                orderEventService.processSuccess(event);
            } catch (Exception e) {
                log.error("재고 차감 실패 - orderId: {}, 사유: {}", event.getOrderId(), e.getMessage());
                orderEventService.processFailure(event);
            }
        } catch (Exception e) {
            log.error("order-events 메시지 파싱 실패: {}", rawPayload, e);
        }
    }
}
