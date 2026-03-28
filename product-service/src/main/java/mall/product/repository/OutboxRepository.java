package mall.product.repository;

import mall.product.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    boolean existsByAggregateId(String aggregateId);
}
