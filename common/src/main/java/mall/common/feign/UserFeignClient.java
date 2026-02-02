package mall.common.feign;

import mall.common.dto.UserResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient("user-service")
public interface UserFeignClient {
    @GetMapping("/users/me")
    UserResponseDto findById(@RequestHeader("Authorization") String authHeader);
}
