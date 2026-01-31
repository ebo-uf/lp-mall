package mall.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 로그인 응답 - Access token 제공
@Getter
@AllArgsConstructor
public class LoginResponseDto {

	private String accessToken;

}
