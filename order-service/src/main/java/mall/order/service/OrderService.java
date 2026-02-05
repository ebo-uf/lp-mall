package mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.domain.OrderStatus;
import mall.common.dto.OrderCreatedEvent;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import mall.common.security.JwtTokenParser;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.entity.Order;
import mall.order.kafka.OrderEventProducer;
import mall.order.repository.OrderRepository;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
    private final OrderEventProducer orderEventProducer;

    public void createOrder(String accessToken, OrderCreateRequestDto orderRequest) {

        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        Long productId = orderRequest.getProductId();
        int quantity = orderRequest.getQuantity();

        ProductResponseDto productResponseDto = productFeignClient.getProduct(productId);

        productFeignClient.reduceStock(productId, quantity);

        Order order = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .status(OrderStatus.COMPLETED)
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

        RLock lock = redissonClient.getLock("lock:product:" + productId);

        try {
            if (!lock.tryLock(5, 1, TimeUnit.SECONDS)) {
                throw new RuntimeException("접속자가 많아 주문이 지연되고 있습니다.");
            }

            decreaseRedisStock(productId);

            try {
                Long price = getProductPrice(productId);
                String orderId = UUID.randomUUID().toString();

                Order order = Order.builder()
                        .orderId(orderId)
                        .status(OrderStatus.PENDING)
                        .userId(userId)
                        .productId(productId)
                        .quantity(quantity)
                        .totalPrice(price * quantity)
                        .build();

                orderRepository.save(order);

                OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId, productId, quantity);
                orderEventProducer.sendOrderEvent(event);

                log.info("주문 접수 완료 (PENDING): orderId = {}", orderId);

            } catch (Exception e) {
                rollbackRedisStock(productId);
                throw e;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("주문 처리 중 시스템 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void decreaseRedisStock(Long productId) {
        RAtomicLong stock = redissonClient.getAtomicLong("stock:product:" + productId);
        if (stock.decrementAndGet() < 0) {
            stock.incrementAndGet();
            throw new RuntimeException("매진되었습니다.");
        }
    }

    private void rollbackRedisStock(Long productId) {
        RAtomicLong stock = redissonClient.getAtomicLong("stock:product:" + productId);
        stock.incrementAndGet();
        log.info("로직 실패로 인한 Redis 재고 롤백 완료 - 상품 ID: {}", productId);
    }

    private Long getProductPrice(Long productId) {
        RBucket<String> priceBucket = redissonClient.getBucket("price:product:" + productId);
        String priceStr = priceBucket.get();
        if (priceStr == null) throw new RuntimeException("상품 정보(가격)를 찾을 수 없습니다.");
        return Long.parseLong(priceStr);
    }

    public void completeOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.COMPLETED);
            log.info("주문 완료 처리 성공: orderId = {}", orderId);
        }
    }

    public void cancelOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELED);
            log.info("주문 취소 처리 완료: orderId = {}", orderId);
        }
    }
}
