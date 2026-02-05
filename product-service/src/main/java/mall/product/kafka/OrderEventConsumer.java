package mall.product.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.product.service.ProductService;
import mall.common.dto.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ProductService productService;
    private final ProductResultProducer productResultProducer;

    @KafkaListener(topics = "order-events", groupId = "product-service-group")
    public void handleOrder(OrderCreatedEvent event) {
        try {
            productService.reduceStock(event.getProductId(), event.getQuantity());

            productResultProducer.sendProductResult(event.getOrderId(), "SUCCESS", event.getProductId());
            log.info("재고 차감 성공");
        } catch (Exception e) {
            productResultProducer.sendProductResult(event.getOrderId(), "FAILURE", event.getProductId());
            log.info("재고 차감 실패");
        }
    }
}