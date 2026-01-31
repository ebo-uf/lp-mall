package mall.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class SessionTokenRepository {

	private final StringRedisTemplate redisTemplate;

	private static final String PREFIX = "session_token";

	public void save(String uid, String refreshToken, long ttlSeconds) {
		redisTemplate.opsForValue().set(PREFIX + uid, refreshToken, ttlSeconds, TimeUnit.SECONDS);
	}

	public Optional<String> find(String uid) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + uid));
	}

	public void delete(String uid) {
		redisTemplate.delete(PREFIX + uid);
	}

}
