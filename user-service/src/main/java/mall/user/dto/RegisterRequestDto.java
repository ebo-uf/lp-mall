package mall.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterRequestDto {

	private String username; // 로그인 시 사용 id

	private String password; // 로그인 시 사용 pw

	private String name;

	private String email;

}
