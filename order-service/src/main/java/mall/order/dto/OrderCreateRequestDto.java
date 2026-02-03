package mall.order.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCreateRequestDto {

    private Long productId;
    private int quantity;

}
