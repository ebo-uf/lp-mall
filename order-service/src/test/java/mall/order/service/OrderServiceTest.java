package mall.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import mall.common.domain.OrderStatus;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import mall.common.security.JwtTokenParser;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.entity.Order;
import mall.order.entity.OutboxEvent;
import mall.order.repository.OrderRepository;
import mall.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock ProductFeignClient productFeignClient;
    @Mock JwtTokenParser jwtTokenParser;
    @Mock RedissonClient redissonClient;

    @InjectMocks
    OrderService orderService;

    @Mock RAtomicLong rAtomicLong;
    @Mock RBucket<String> openAtBucket;  // openAt 전용
    @Mock RBucket<String> priceBucket;   // price 전용
    @Mock Claims claims;

    @BeforeEach
    void setUp() throws Exception {
        var field = OrderService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(orderService, new ObjectMapper());
    }

    // ──────────────────────────────────────────────
    // reserveOrder
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("limited order reserve success")
    void reserveOrder_limited_success() {
        given(jwtTokenParser.parseClaimsAllowExpired(anyString())).willReturn(claims);
        given(claims.get("userId", String.class)).willReturn("user-1");

        doReturn(openAtBucket).when(redissonClient).getBucket("openAt:product:1");
        given(openAtBucket.get()).willReturn(LocalDateTime.now().minusHours(1).toString());

        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");
        given(rAtomicLong.decrementAndGet()).willReturn(4L);

        doReturn(priceBucket).when(redissonClient).getBucket("price:product:1");
        given(priceBucket.get()).willReturn("50000");

        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderCreateRequestDto request = OrderCreateRequestDto.builder()
                .productId(1L).quantity(1).isLimited(true).build();

        String orderId = orderService.reserveOrder("token", request);

        assertThat(orderId).isNotBlank();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(captor.getValue().isLimited()).isTrue();
        assertThat(captor.getValue().getTotalPrice()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("limited order reserve fail - sold out, Redis rollback")
    void reserveOrder_limited_soldOut() {
        given(jwtTokenParser.parseClaimsAllowExpired(anyString())).willReturn(claims);
        given(claims.get("userId", String.class)).willReturn("user-1");

        doReturn(openAtBucket).when(redissonClient).getBucket("openAt:product:1");
        given(openAtBucket.get()).willReturn(LocalDateTime.now().minusHours(1).toString());

        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");
        given(rAtomicLong.decrementAndGet()).willReturn(-1L);

        OrderCreateRequestDto request = OrderCreateRequestDto.builder()
                .productId(1L).quantity(1).isLimited(true).build();

        assertThatThrownBy(() -> orderService.reserveOrder("token", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("매진");

        then(rAtomicLong).should().incrementAndGet(); // Redis rollback
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("limited order reserve fail - before sale time")
    void reserveOrder_limited_beforeSaleTime() {
        given(jwtTokenParser.parseClaimsAllowExpired(anyString())).willReturn(claims);
        given(claims.get("userId", String.class)).willReturn("user-1");

        doReturn(openAtBucket).when(redissonClient).getBucket("openAt:product:1");
        given(openAtBucket.get()).willReturn(LocalDateTime.now().plusHours(1).toString());

        OrderCreateRequestDto request = OrderCreateRequestDto.builder()
                .productId(1L).quantity(1).isLimited(true).build();

        assertThatThrownBy(() -> orderService.reserveOrder("token", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("판매 시간");

        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("limited order reserve fail - DB save error, Redis rollback")
    void reserveOrder_limited_dbSaveFail_redisRollback() {
        given(jwtTokenParser.parseClaimsAllowExpired(anyString())).willReturn(claims);
        given(claims.get("userId", String.class)).willReturn("user-1");

        doReturn(openAtBucket).when(redissonClient).getBucket("openAt:product:1");
        given(openAtBucket.get()).willReturn(LocalDateTime.now().minusHours(1).toString());

        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");
        given(rAtomicLong.decrementAndGet()).willReturn(3L);

        doReturn(priceBucket).when(redissonClient).getBucket("price:product:1");
        given(priceBucket.get()).willReturn("50000");

        given(orderRepository.save(any())).willThrow(new RuntimeException("DB error"));

        OrderCreateRequestDto request = OrderCreateRequestDto.builder()
                .productId(1L).quantity(1).isLimited(true).build();

        assertThatThrownBy(() -> orderService.reserveOrder("token", request))
                .isInstanceOf(RuntimeException.class);

        then(rAtomicLong).should().incrementAndGet(); // Redis rollback
    }

    @Test
    @DisplayName("regular product reserve success - price from Feign")
    void reserveOrder_regular_success() {
        given(jwtTokenParser.parseClaimsAllowExpired(anyString())).willReturn(claims);
        given(claims.get("userId", String.class)).willReturn("user-1");
        given(productFeignClient.getProduct(1L))
                .willReturn(ProductResponseDto.builder().price(30000L).build());
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderCreateRequestDto request = OrderCreateRequestDto.builder()
                .productId(1L).quantity(2).isLimited(false).build();

        String orderId = orderService.reserveOrder("token", request);

        assertThat(orderId).isNotBlank();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(captor.getValue().isLimited()).isFalse();
        assertThat(captor.getValue().getTotalPrice()).isEqualTo(60000L);
        then(redissonClient).shouldHaveNoInteractions();
    }

    // ──────────────────────────────────────────────
    // cancelReservation
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cancel PENDING limited order - status CANCELED, Redis stock restored")
    void cancelReservation_pendingLimited_redisRestored() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PENDING)
                .productId(1L).isLimited(true).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));
        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");

        orderService.cancelReservation("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        then(rAtomicLong).should().incrementAndGet();
    }

    @Test
    @DisplayName("cancel PENDING regular order - status CANCELED, no Redis call")
    void cancelReservation_pendingRegular_noRedis() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PENDING)
                .productId(1L).isLimited(false).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.cancelReservation("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        then(redissonClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("cancel already-processed order - no status change (idempotent)")
    void cancelReservation_alreadyProcessed_noChange() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.COMPLETED)
                .productId(1L).isLimited(true).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.cancelReservation("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        then(redissonClient).shouldHaveNoInteractions();
    }

    // ──────────────────────────────────────────────
    // handlePaymentCompleted
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("payment completed - status PAYMENT_COMPLETED, outbox order-events saved")
    void handlePaymentCompleted_success() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PENDING)
                .userId("user-1").productId(1L).quantity(1).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.handlePaymentCompleted("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("order-events");
        assertThat(captor.getValue().getType()).isEqualTo("OrderCreatedEvent");
        assertThat(captor.getValue().getPayload()).contains("ord-1");
    }

    @Test
    @DisplayName("payment completed - already processed, no outbox saved (idempotent)")
    void handlePaymentCompleted_alreadyProcessed_idempotent() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PAYMENT_COMPLETED)
                .userId("user-1").productId(1L).quantity(1).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.handlePaymentCompleted("ord-1");

        then(outboxRepository).should(never()).save(any());
    }

    // ──────────────────────────────────────────────
    // completeOrder
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("complete order - PAYMENT_COMPLETED -> COMPLETED")
    void completeOrder_success() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PAYMENT_COMPLETED).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.completeOrder("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // ──────────────────────────────────────────────
    // cancelOrder (Saga compensation)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cancel order (Saga compensation) - CANCELED + Redis rollback + payment-refund outbox")
    void cancelOrder_sagaCompensation_limited() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.PAYMENT_COMPLETED)
                .productId(1L).isLimited(true).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));
        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");

        orderService.cancelOrder("ord-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        then(rAtomicLong).should().incrementAndGet(); // Redis stock restored
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("payment-refund");
        assertThat(captor.getValue().getType()).isEqualTo("PaymentRefundEvent");
        assertThat(captor.getValue().getPayload()).contains("ord-1");
    }

    @Test
    @DisplayName("cancel order (Saga compensation) - not PAYMENT_COMPLETED, no action")
    void cancelOrder_notPaymentCompleted_noAction() {
        Order order = Order.builder().orderId("ord-1").status(OrderStatus.CANCELED)
                .productId(1L).isLimited(true).build();
        given(orderRepository.findByOrderId("ord-1")).willReturn(Optional.of(order));

        orderService.cancelOrder("ord-1");

        then(redissonClient).shouldHaveNoInteractions();
        then(outboxRepository).should(never()).save(any());
    }
}
