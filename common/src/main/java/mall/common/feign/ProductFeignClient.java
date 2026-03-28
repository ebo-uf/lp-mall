package mall.common.feign;

import mall.common.dto.ProductResponseDto;
import mall.common.feign.fallback.ProductFeignFallBackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", fallbackFactory = ProductFeignFallBackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/products/{productId}")
    ProductResponseDto getProduct(@PathVariable Long productId);
}
