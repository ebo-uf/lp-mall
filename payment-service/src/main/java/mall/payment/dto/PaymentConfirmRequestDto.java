package mall.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequestDto {
    private String paymentKey;
    private String tossOrderId; // 토스 orderId (= 우리가 만든 orderId)
    private Long amount;
}
