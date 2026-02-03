package mall.order.service;

import lombok.RequiredArgsConstructor;
import mall.common.dto.ProductResponseDto;
import mall.common.feign.ProductFeignClient;
import mall.common.security.JwtTokenParser;
import mall.order.dto.OrderCreateRequestDto;
import mall.order.entity.Order;
import mall.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductFeignClient productFeignClient;
    private final JwtTokenParser jwtTokenParser;

    public void createOrder(String accessToken, OrderCreateRequestDto orderCreateRequestDto) {

        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        Long productId = orderCreateRequestDto.getProductId();
        int quantity = orderCreateRequestDto.getQuantity();

        ProductResponseDto productResponseDto = productFeignClient.getProduct(productId);

        productFeignClient.reduceStock(productId, quantity);

        Order order = Order.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(productResponseDto.getPrice() * quantity)
                .build();

        orderRepository.save(order);
    }
}
