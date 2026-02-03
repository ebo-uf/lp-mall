package mall.product.controller;

import lombok.RequiredArgsConstructor;
import mall.common.dto.ProductResponseDto;
import mall.common.dto.UserResponseDto;
import mall.common.feign.UserFeignClient;
import mall.product.dto.ProductCreateRequestDto;
import mall.product.dto.ProductFindResponseDto;
import mall.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final UserFeignClient userFeignClient;

    @GetMapping("/all")
    public ResponseEntity<List<ProductFindResponseDto>> findAllProducts() {
        return ResponseEntity.ok(productService.findAllProducts());
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createProduct(@RequestBody ProductCreateRequestDto productCreateRequestDto) {
        UserResponseDto user = userFeignClient.findById();
        productService.createProduct(productCreateRequestDto, user.getUserId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/reduce-stock/{productId}")
    public ResponseEntity<Void> reduceStock(@PathVariable Long productId,
                                            @RequestParam Integer quantity) {
        productService.reduceStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponseDto> findById(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.findById(productId));
    }

    @GetMapping("/health")
    public String status() {
        return "Product Service is working properly!";
    }
}
