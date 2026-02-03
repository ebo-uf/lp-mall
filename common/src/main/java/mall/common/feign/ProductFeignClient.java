package mall.common.feign;

import mall.common.dto.ProductResponseDto;
import mall.common.feign.fallback.ProductFeignFallBackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service", fallbackFactory = ProductFeignFallBackFactory.class)
public interface ProductFeignClient {
    @PutMapping("/products/reduce-stock/{productId}")
    void reduceStock(@PathVariable Long productId,
                     @RequestParam("quantity") int quantity);

    @GetMapping("/products/{productId}")
    ProductResponseDto getProduct(@PathVariable("productId") Long productId);
}
