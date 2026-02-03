package mall.common.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {
    private String userId;
    private String username;
    private String name;
    private String email;
}
