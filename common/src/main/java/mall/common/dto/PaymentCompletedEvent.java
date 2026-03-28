package mall.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String orderId;
    private String paymentKey;
    private Long amount;
}
