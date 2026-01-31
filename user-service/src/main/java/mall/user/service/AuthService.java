package mall.user.service;

import mall.user.dto.*;
import mall.user.entity.User;
import mall.user.exception.custom.DuplicateUserException;
import mall.user.exception.custom.InvalidPasswordException;
import mall.user.exception.custom.TokenInvalidException;
import mall.user.exception.custom.UserNotFoundException;
import mall.user.repository.SessionTokenRepository;
import mall.user.repository.UserRepository;
import mall.common.security.JwtTokenParser;
import mall.common.security.SessionTokenConfig;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mall.user.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	private final JwtTokenParser jwtTokenParser;

	private final JwtTokenProvider jwtTokenProvider;

	private final SessionTokenRepository sessionTokenRepository;

	private final PasswordEncoder passwordEncoder;

	public User getUser(String username) {
		return userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
	}

	// Login Service 구현
	public LoginResponseDto login(LoginRequestDto request) {
		// 1. DB에서 사용자 정보 가져오기
		User user = getUser(request.getUsername());
		// 2. ID/PW 검증부
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new InvalidPasswordException();
		}
		// JWT 토큰 생성부
		String accessToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.ACCESS);
		String refreshToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.REFRESH);
		sessionTokenRepository.save(user.getUserId(), refreshToken, SessionTokenConfig.REFRESH.getExpireMillis() / 1000);
		return new LoginResponseDto(accessToken);
	}

	@Transactional // 원자성 보장을 위해서 추가
	public RegisterResponseDto register(RegisterRequestDto request) {

		// 1. username 중복 검사
		validateDuplicateUsername(request.getUsername());

		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// 4. User entity 생성
		User user = User.builder()
			.username(request.getUsername())
			.password(encodedPassword)
			.name(request.getName())
			.email(request.getEmail())
			.build();

		// 5. DB에 저장
		userRepository.save(user);
		return RegisterResponseDto.success();
	}

	public RefreshResponseDto refresh(String token) {
		// 1. Token에서 uid 추출
		Claims claims = jwtTokenParser.parseClaimsAllowExpired(token);
		String uid = claims.get("uid", String.class);

		// 2. Redis에 해당 토큰이 존재하는지 검색 -> 없으면 에러
		String sessionRefreshToken = sessionTokenRepository.find(uid).orElseThrow(TokenInvalidException::new);

		// 3. 해당 토큰이 존재한다면 기존 토큰을 제거
		if (!jwtTokenParser.isValidateToken(sessionRefreshToken)) {
			sessionTokenRepository.delete(uid);
			throw new TokenInvalidException();
		}

		// 4. User table에서 검색 후 JWT 토큰 생성
		User user = userRepository.findByUserId(uid).orElseThrow(UserNotFoundException::new);
		String accessToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.ACCESS);
		String refreshToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.REFRESH);

		// 5. Refresh Token을 새로 등록
		sessionTokenRepository.save(uid, refreshToken, SessionTokenConfig.REFRESH.getExpireMillis() / 1000);

		// 6. accessToken 보내기
		return new RefreshResponseDto(accessToken);
	}

	public LogoutResponseDto logout(String token) {
		Claims claims = jwtTokenParser.parseClaims(token);
		sessionTokenRepository.delete(claims.get("uid", String.class));

		return LogoutResponseDto.success();
	}

	private void validateDuplicateUsername(String username) {
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateUserException();
		}
	}

}