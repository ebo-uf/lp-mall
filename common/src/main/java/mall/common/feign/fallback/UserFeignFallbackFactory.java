package mall.common.feign.fallback;

import lombok.extern.slf4j.Slf4j;
import mall.common.dto.UserResponseDto;
import mall.common.feign.UserFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {
    @Override
    public UserFeignClient create(Throwable cause) {
        return new UserFeignClient() {
            @Override
            public UserResponseDto findById(String authHeader) {
                log.error("User Service findById 호출 중 에러 발생: {}", cause.getMessage());
                throw new RuntimeException("현재 유저 서비스 이용이 불가능합니다. 잠시 후 다시 시도해주세요.");
            }
        };
    }
}
