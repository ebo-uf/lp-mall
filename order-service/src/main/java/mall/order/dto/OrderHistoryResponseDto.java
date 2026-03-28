package mall.order.dto;

import lombok.Builder;
import lombok.Getter;
import mall.common.domain.OrderStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class OrderHistoryResponseDto {
    private String orderId;
    private OrderStatus status;
    private Long productId;
    private String productName;
    private int quantity;
    private Long totalPrice;
    private boolean isLimited;
    private LocalDateTime createdAt;
}
