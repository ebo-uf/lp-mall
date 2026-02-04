package mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.OrderCreatedEvent;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import mall.common.security.JwtTokenParser;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.entity.Order;
import mall.order.repository.OrderRepository;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductFeignClient productFeignClient;
    private final JwtTokenParser jwtTokenParser;
    private final RedissonClient redissonClient;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void createOrder(String accessToken, OrderCreateRequestDto orderRequest) {

        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        Long productId = orderRequest.getProductId();
        int quantity = orderRequest.getQuantity();

        ProductResponseDto productResponseDto = productFeignClient.getProduct(productId);

        productFeignClient.reduceStock(productId, quantity);

        Order order = Order.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(productResponseDto.getPrice() * quantity)
                .build();

        orderRepository.save(order);
    }

    public void createLimitedOrder(String accessToken, OrderCreateRequestDto orderRequest) {
        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        Long productId = orderRequest.getProductId();
        int quantity = orderRequest.getQuantity();

        String lockKey = "lock:product:" + productId;
        String stockKey = "stock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean lockAcquired = lock.tryLock(5, 1, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.info("락 획득 실패 - 유저: {}, 상품: {}", userId, productId);
                throw new RuntimeException("현재 접속자가 많아 주문이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
            }

            RAtomicLong stock = redissonClient.getAtomicLong(stockKey);

            if (stock.decrementAndGet() < 0) {
                stock.incrementAndGet();
                log.info("매진되었습니다 - 유저: {}", userId);
                throw new RuntimeException("매진되었습니다.");
            }

            String orderId = UUID.randomUUID().toString();
            OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId, productId, quantity);

            kafkaTemplate.send("order-events", event);
            log.info("kafka 이벤트 발행 완료: orderId = {}, userId = {}, productId = {}, quantity = {}",
                    orderId, userId, productId, quantity);

        } catch (InterruptedException e) {
            throw new RuntimeException("주문 처리 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
