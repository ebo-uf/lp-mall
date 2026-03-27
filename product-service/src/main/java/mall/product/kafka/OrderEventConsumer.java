package mall.product.kafka;

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

    @KafkaListener(topics = "order-events", groupId = "product-service-group")
    public void handleOrder(OrderCreatedEvent event) {
        try {
            orderEventService.processSuccess(event);
        } catch (Exception e) {
            log.error("재고 차감 실패 - orderId: {}, 사유: {}", event.getOrderId(), e.getMessage());
            orderEventService.processFailure(event);
        }
    }
}
