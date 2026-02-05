package mall.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.common.dto.ProductResponseDto;
import mall.product.dto.ProductCreateRequestDto;
import mall.product.dto.ProductFindResponseDto;
import mall.product.entity.Product;
import mall.product.repository.ProductRepository;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    public List<ProductFindResponseDto> findAllProducts(){
        return productRepository.findAll().stream()
                .map(product -> ProductFindResponseDto.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .artistName(product.getArtistName())
                        .year(product.getYear())
                        .condition(product.getCondition())
                        .price(product.getPrice())
                        .stock(product.getStock())
                        .sellerId(product.getSellerId())
                        .saleStartAt(product.getSaleStartAt())
                        .isLimited(product.getIsLimited())
                        .createdAt(product.getCreatedAt())
                        .updatedAt(product.getUpdatedAt())
                        .build())
                .toList();
    }

    public ProductResponseDto findById(Long productId){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("제품이 존재하지 않습니다."));
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .artistName(product.getArtistName())
                .year(product.getYear())
                .condition(product.getCondition())
                .price(product.getPrice())
                .stock(product.getStock())
                .sellerId(product.getSellerId())
                .saleStartAt(product.getSaleStartAt())
                .isLimited(product.getIsLimited())
                .build();
    }

    public void createProduct(ProductCreateRequestDto productDto, String sellerId){
        Product product = Product.builder()
                .name(productDto.getName())
                .artistName(productDto.getArtistName())
                .year(productDto.getYear())
                .condition(productDto.getCondition())
                .price(productDto.getPrice())
                .stock(productDto.getStock())
                .sellerId(sellerId)
                .saleStartAt(productDto.getSaleStartAt())
                .isLimited(productDto.getIsLimited())
                .build();

        productRepository.save(product);

        if(productDto.getIsLimited()) {
            Long productId = product.getId();
            redissonClient.getAtomicLong("stock:product:" + productId).set(productDto.getStock());
            redissonClient.getBucket("price:product:" + productId).set(productDto.getPrice());
            redissonClient.getBucket("openAt:product:" + productId).set(productDto.getSaleStartAt());
            log.info("한정판 상품 캐싱 완료 productId: {}", productId);
        }
    }

    public void reduceStock(Long productId, Integer quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("해당 상품이 존재하지 않습니다. productId: " + productId));

        product.removeStock(quantity);
    }
}
