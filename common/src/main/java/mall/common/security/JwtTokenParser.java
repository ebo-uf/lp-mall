package mall.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtTokenParser {

	@Value("${jwt.secret}")
	private String secret;

	private Key key;

	// application.yml에 설정해 둔 정보를 기반(시크릿 키, 만료 시간)으로 TokenProvider를 생성
	@PostConstruct
	private void init() {
		if (secret == null || secret.length() < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 characters");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	// token 파싱
	public Claims parseClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
	}

	public Claims parseClaimsAllowExpired(String token) {
		try {
			return parseClaims(token);
		}
		catch (ExpiredJwtException e) {
			return e.getClaims();
		}
	}

	// Access Token인지 검증
	public boolean isAccessToken(Claims claims) {
		return SessionTokenConfig.ACCESS.name().equals(claims.get("type", String.class));
	}

	public Claims validateAccessToken(String token) {
		try {
			return parseClaims(token);
		}
		catch (ExpiredJwtException e) {
			throw new JwtException("ACCESS_TOKEN_EXPIRED");
		}
		catch (JwtException | IllegalArgumentException e) {
			throw new JwtException("INVALID_ACCESS_TOKEN");
		}
	}

	public boolean isValidateToken(String token) {
		try {
			parseClaims(token);
			return true;
		}
		catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

}
