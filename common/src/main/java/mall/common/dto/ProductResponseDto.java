package mall.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductResponseDto {
    private Long id;
    private String name;
    private String artistName;
    private Integer year;
    private String condition;
    private Long price;
    private Integer stock;
    private String sellerId;
    private LocalDateTime saleStartAt;
    private Boolean isLimited;
    private String thumbnailPath;
}
