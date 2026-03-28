package mall.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import mall.common.dto.OrderCreatedEvent;
import mall.product.entity.OutboxEvent;
import mall.product.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock ProductService productService;
    @Mock OutboxRepository outboxRepository;

    @InjectMocks
    OrderEventService orderEventService;

    @BeforeEach
    void setUp() throws Exception {
        var field = OrderEventService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(orderEventService, new ObjectMapper());
    }

    // ──────────────────────────────────────────────
    // processSuccess
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("재고 차감 성공 - DB 재고 감소 + SUCCESS outbox 저장")
    void processSuccess_성공() {
        OrderCreatedEvent event = new OrderCreatedEvent("ord-1", "user-1", 1L, 2);
        willDoNothing().given(productService).reduceStock(1L, 2);

        orderEventService.processSuccess(event);

        then(productService).should().reduceStock(1L, 2);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("product-results");
        assertThat(captor.getValue().getType()).isEqualTo("ProductResultEvent");
        assertThat(captor.getValue().getPayload()).contains("ord-1").contains("SUCCESS");
    }

    @Test
    @DisplayName("재고 차감 실패 - outbox 저장 안 함 (같은 트랜잭션 롤백)")
    void processSuccess_재고부족() {
        OrderCreatedEvent event = new OrderCreatedEvent("ord-1", "user-1", 1L, 999);
        willThrow(new RuntimeException("상품의 재고가 부족합니다."))
                .given(productService).reduceStock(1L, 999);

        assertThatThrownBy(() -> orderEventService.processSuccess(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("재고가 부족");

        // 같은 트랜잭션이므로 outbox도 저장되지 않아야 함
        then(outboxRepository).should(never()).save(any());
    }

    // ──────────────────────────────────────────────
    // processFailure
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("재고 차감 실패 처리 - 재고 변경 없이 FAILURE outbox 저장")
    void processFailure_성공() {
        OrderCreatedEvent event = new OrderCreatedEvent("ord-1", "user-1", 1L, 999);

        orderEventService.processFailure(event);

        then(productService).should(never()).reduceStock(anyLong(), anyInt());
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        then(outboxRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo("product-results");
        assertThat(captor.getValue().getType()).isEqualTo("ProductResultEvent");
        assertThat(captor.getValue().getPayload()).contains("ord-1").contains("FAILURE");
    }
}
