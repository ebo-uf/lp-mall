package mall.user.service;

import lombok.RequiredArgsConstructor;
import mall.common.dto.UserResponseDto;
import mall.common.security.JwtTokenParser;
import mall.user.entity.User;
import mall.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtTokenParser jwtTokenParser;

    public UserResponseDto findById(String accessToken) {
        String userId = jwtTokenParser.parseClaimsAllowExpired(accessToken).get("userId", String.class);
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("해당 유저를 찾을 수 없습니다."));

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
