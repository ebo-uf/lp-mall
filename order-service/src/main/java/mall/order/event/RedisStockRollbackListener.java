package mall.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockRollbackListener {

    private final RedissonClient redissonClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRollback(RedisStockRollbackEvent event) {
        redissonClient.getAtomicLong("stock:product:" + event.productId()).incrementAndGet();
        log.info("Redis 재고 롤백 (after commit): productId={}", event.productId());
    }
}
