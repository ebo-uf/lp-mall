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
import org.redisson.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
//import java.util.Collections;
import java.util.UUID;

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
        checkDate(orderRequest.getProductId());

        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        Long productId = orderRequest.getProductId();
        int quantity = orderRequest.getQuantity();

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
    }

    private void checkDate(Long productId) {
        RBucket<String> openAtStr = redissonClient.getBucket("openAt:product:" + productId);
        LocalDateTime openAt = LocalDateTime.parse(openAtStr.get());

        if (LocalDateTime.now().isBefore(openAt)) {
            throw new RuntimeException("아직 판매 시간이 아닙니다.");
        }
    }

    private void decreaseRedisStock(Long productId) {
        RAtomicLong stock = redissonClient.getAtomicLong("stock:product:" + productId);
        if (stock.decrementAndGet() < 0) {
            stock.incrementAndGet();
            throw new RuntimeException("매진되었습니다.");
        }
    }

//    private void decreaseRedisStockWithLua(Long productId) {
//        // KEYS[1]: 상품 재고 키 ("stock:product:1")
//        // ARGV[1]: 차감할 수량 (1)
//        String script =
//                "local stock = redis.call('get', KEYS[1]) " +
//                        "if not stock or tonumber(stock) < tonumber(ARGV[1]) then " +
//                        "  return -1 " + // 재고 부족 시 -1 반환
//                        "else " +
//                        "  return redis.call('decrby', KEYS[1], ARGV[1]) " + // 재고 있으면 차감
//                        "end";
//
//        Long result = redissonClient.getScript().eval(
//                RScript.Mode.READ_WRITE,
//                script,
//                RScript.ReturnType.VALUE,
//                Collections.singletonList("stock:product:" + productId),
//                "1" // ARGV[1] 에 전달할 수량
//        );
//
//        if (result == -1) {
//            throw new RuntimeException("매진되었습니다.");
//        }
//    }

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
