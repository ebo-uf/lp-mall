package mall.common.domain;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING("결제 대기"),
    COMPLETED("주문 완료"),
    FAILED("주문 실패"),
    CANCELED("주문 취소");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

}