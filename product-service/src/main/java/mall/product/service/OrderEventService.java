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

    @Transactional
    public void processSuccess(OrderCreatedEvent event) {
        if (outboxRepository.existsByAggregateId(event.getOrderId())) {
            log.warn("중복 이벤트 무시 (processSuccess): orderId={}", event.getOrderId());
            return;
        }
        productService.reduceStock(event.getProductId(), event.getQuantity());
        saveOutbox(event.getOrderId(), "SUCCESS", event.getProductId());
        log.info("재고 차감 성공 - orderId: {}", event.getOrderId());
    }

    @Transactional
    public void processFailure(OrderCreatedEvent event) {
        if (outboxRepository.existsByAggregateId(event.getOrderId())) {
            log.warn("중복 이벤트 무시 (processFailure): orderId={}", event.getOrderId());
            return;
        }
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
