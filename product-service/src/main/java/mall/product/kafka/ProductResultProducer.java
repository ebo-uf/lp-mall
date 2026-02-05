package mall.product.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.ProductResultEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductResultProducer {
    private final KafkaTemplate<String, ProductResultEvent> kafkaTemplate;

    public void sendProductResult(String OrderId, String result, Long productId) {
        ProductResultEvent event = new ProductResultEvent(OrderId,result,productId);
        kafkaTemplate.send("product-results", event);
        log.info("Kafka 이벤트 발행 완료: orderId = {}, result: {}", OrderId,result);
    }
}
