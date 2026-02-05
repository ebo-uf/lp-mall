package mall.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.ProductResultEvent;
import mall.order.service.OrderService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductResultConsumer {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    @KafkaListener(topics = "product-results", groupId = "order-service-group")
    public void handleProductResult(ProductResultEvent event) {
        log.info("상품 처리 결과 수신: orderId = {}, status = {}", event.getOrderId(), event.getStatus());
        if ("SUCCESS".equals(event.getStatus())) {
            orderService.completeOrder(event.getOrderId());
        }
        else {
            orderService.cancelOrder(event.getOrderId());
            rollbackRedisStock(event.getProductId());
        }
    }

    private void rollbackRedisStock(Long productId) {
        String stockKey = "stock:product:" + productId;
        RAtomicLong stock = redissonClient.getAtomicLong(stockKey);
        long currentStock = stock.incrementAndGet();
        log.info("보상 트랜잭션: Redis 재고 복구 완료. 상품ID: {}, 현재재고: {}", productId, currentStock);
    }
}