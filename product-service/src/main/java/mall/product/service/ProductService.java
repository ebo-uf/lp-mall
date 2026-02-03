package mall.product.service;

import lombok.RequiredArgsConstructor;
import mall.product.dto.ProductCreateRequestDto;
import mall.product.dto.ProductFindResponseDto;
import mall.product.entity.Product;
import mall.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

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
    }
}
