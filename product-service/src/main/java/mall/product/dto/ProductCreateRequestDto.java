package mall.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductCreateRequestDto {

    private String name;
    private String artistName;
    private Integer year;
    private String condition;
    private Long price;
    private Integer stock;
    private LocalDateTime saleStartAt;
    private Boolean isLimited;
}
