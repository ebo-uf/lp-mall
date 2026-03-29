package mall.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.domain.OrderStatus;
import mall.common.dto.OrderCreatedEvent;
import mall.common.dto.PaymentRefundEvent;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import mall.common.security.JwtTokenParser;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.dto.OrderHistoryResponseDto;
import mall.order.entity.Order;
import mall.order.entity.OutboxEvent;
import mall.order.event.RedisStockRollbackEvent;
import mall.order.repository.OrderRepository;
import mall.order.repository.OutboxRepository;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ProductFeignClient productFeignClient;
    private final JwtTokenParser jwtTokenParser;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public String reserveOrder(String accessToken, OrderCreateRequestDto request) {
        boolean limited = Boolean.TRUE.equals(request.getIsLimited());
        Long productId = request.getProductId();
        int quantity = request.getQuantity();
        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);

        Long price;
        if (limited) {
            checkDate(productId);
            decreaseRedisStock(productId);
            price = getProductPriceFromRedis(productId);
        } else {
            ProductResponseDto product = productFeignClient.getProduct(productId);
            price = product.getPrice();
        }

        String orderId = UUID.randomUUID().toString();
        try {
            Order order = Order.builder()
                    .orderId(orderId)
                    .status(OrderStatus.PENDING)
                    .userId(userId)
                    .productId(productId)
                    .quantity(quantity)
                    .totalPrice(price * quantity)
                    .isLimited(limited)
                    .build();
            orderRepository.save(order);
            log.info("재고 점유 완료 (PENDING): orderId={}", orderId);
            return orderId;
        } catch (Exception e) {
            if (limited) rollbackRedisStock(productId);
            throw e;
        }
    }

    public void cancelReservation(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELED);
            if (order.isLimited()) rollbackRedisStock(order.getProductId());
            log.info("주문 취소 (결제 이탈): orderId={}", orderId);
        }
    }

    public void handlePaymentCompleted(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("이미 처리된 주문: orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_COMPLETED);

        try {
            String payload = objectMapper.writeValueAsString(
                    new OrderCreatedEvent(orderId, order.getUserId(), order.getProductId(), order.getQuantity()));
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType("order-events")
                    .aggregateId(orderId)
                    .type("OrderCreatedEvent")
                    .payload(payload)
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 직렬화 실패", e);
        }

        log.info("결제 완료 처리: orderId={}", orderId);
    }

    public void completeOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
            order.setStatus(OrderStatus.COMPLETED);
            log.info("주문 완료: orderId={}", orderId);
        }
    }

    public void cancelOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
            order.setStatus(OrderStatus.CANCELED);

            try {
                String payload = objectMapper.writeValueAsString(new PaymentRefundEvent(orderId));
                outboxRepository.save(OutboxEvent.builder()
                        .aggregateType("payment-refund")
                        .aggregateId(orderId)
                        .type("PaymentRefundEvent")
                        .payload(payload)
                        .build());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Outbox 직렬화 실패", e);
            }

            if (order.isLimited()) eventPublisher.publishEvent(new RedisStockRollbackEvent(order.getProductId()));

            log.info("주문 취소 + 환불 이벤트 발행: orderId={}", orderId);
        }
    }

    public List<OrderHistoryResponseDto> getMyOrders(String accessToken) {
        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> {
                    String productName = fetchProductName(order.getProductId());
                    return OrderHistoryResponseDto.builder()
                            .orderId(order.getOrderId())
                            .status(order.getStatus())
                            .productId(order.getProductId())
                            .productName(productName)
                            .quantity(order.getQuantity())
                            .totalPrice(order.getTotalPrice())
                            .isLimited(order.isLimited())
                            .createdAt(order.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private String fetchProductName(Long productId) {
        try {
            return productFeignClient.getProduct(productId).getName();
        } catch (Exception e) {
            return "상품 정보 없음";
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

    private void rollbackRedisStock(Long productId) {
        redissonClient.getAtomicLong("stock:product:" + productId).incrementAndGet();
        log.info("Redis 재고 롤백: productId={}", productId);
    }

    private Long getProductPriceFromRedis(Long productId) {
        String priceStr = redissonClient.<String>getBucket("price:product:" + productId).get();
        if (priceStr == null) throw new RuntimeException("상품 가격 정보를 찾을 수 없습니다.");
        return Long.parseLong(priceStr);
    }
}
