package mall.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.OrderCreatedEvent;
import mall.common.dto.ProductResultEvent;
import mall.product.entity.OutboxEvent;
import mall.product.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventService {

    private final ProductService productService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 재고 차감 + SUCCESS outbox — 하나의 트랜잭션
    // reduceStock 실패 시 outbox도 함께 롤백 → 예외 전파
    @Transactional
    public void processSuccess(OrderCreatedEvent event) {
        productService.reduceStock(event.getProductId(), event.getQuantity());
        saveOutbox(event.getOrderId(), "SUCCESS", event.getProductId());
        log.info("재고 차감 성공 - orderId: {}", event.getOrderId());
    }

    // FAILURE outbox — 독립 트랜잭션
    // processSuccess 롤백 상태에서 호출되므로 별도 트랜잭션으로 FAILURE 이벤트 보장
    @Transactional
    public void processFailure(OrderCreatedEvent event) {
        saveOutbox(event.getOrderId(), "FAILURE", event.getProductId());
        log.info("재고 차감 실패 처리 - orderId: {}", event.getOrderId());
    }

    private void saveOutbox(String orderId, String status, Long productId) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new ProductResultEvent(orderId, status, productId));
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType("product-results")
                    .aggregateId(orderId)
                    .type("ProductResultEvent")
                    .payload(payload)
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 직렬화 실패", e);
        }
    }
}
