package mall.common.feign;

import mall.common.dto.UserResponseDto;
import mall.common.feign.fallback.UserFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", fallbackFactory = UserFeignFallbackFactory.class)
public interface UserFeignClient {
    @GetMapping("/users/me")
    UserResponseDto findById(@RequestHeader("Authorization") String authHeader);
}
