package mall.order.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class RedisStockRollbackListenerTest {

    @Mock RedissonClient redissonClient;
    @Mock RAtomicLong rAtomicLong;

    @InjectMocks
    RedisStockRollbackListener listener;

    @Test
    @DisplayName("AFTER_COMMIT event - Redis stock incremented for correct productId")
    void handleRollback_incrementsRedisStock() {
        doReturn(rAtomicLong).when(redissonClient).getAtomicLong("stock:product:1");

        listener.handleRollback(new RedisStockRollbackEvent(1L));

        then(rAtomicLong).should().incrementAndGet();
    }
}
