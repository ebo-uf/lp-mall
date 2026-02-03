package mall.common.feign.fallback;

import lombok.extern.slf4j.Slf4j;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductFeignFallBackFactory implements FallbackFactory<ProductFeignClient> {
    @Override
    public ProductFeignClient create(Throwable cause) {
        return new ProductFeignClient() {
            @Override
            public void reduceStock(Long productId, int quantity) {
                log.error("Product Service reduceStock 호출 중 에러 발생: {}", cause.getMessage());
                throw new RuntimeException("현재 프로덕트 서비스 이용이 불가능합니다. 잠시 후 다시 시도해주세요.");
            }

            @Override
            public ProductResponseDto getProduct(Long productId) {
                log.error("Product Service getProduct 호출 중 에러 발생: {}", cause.getMessage());
                throw new RuntimeException("현재 프로덕트 서비스 이용이 불가능합니다. 잠시 후 다시 시도해주세요.");
            }
        };
    }
}
