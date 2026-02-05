package mall.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.OrderCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void sendOrderEvent(OrderCreatedEvent event) {
        kafkaTemplate.send("order-events", event);
        log.info("Kafka 이벤트 발행 완료: orderId = {}, productId = {}",
                event.getOrderId(), event.getProductId());
    }
}