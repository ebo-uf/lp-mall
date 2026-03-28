package mall.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.ProductResultEvent;
import mall.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductResultConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product-results", groupId = "order-service-group")
    public void handleProductResult(String rawPayload) {
        try {
            String json = rawPayload;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            ProductResultEvent event = objectMapper.readValue(json, ProductResultEvent.class);
            log.info("상품 처리 결과 수신: orderId={}, status={}", event.getOrderId(), event.getStatus());
            if ("SUCCESS".equals(event.getStatus())) {
                orderService.completeOrder(event.getOrderId());
            } else {
                orderService.cancelOrder(event.getOrderId());
            }
        } catch (Exception e) {
            log.error("product-results 메시지 파싱 실패: {}", rawPayload, e);
        }
    }
}
