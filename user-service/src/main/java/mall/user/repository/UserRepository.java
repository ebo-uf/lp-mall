package mall.user.repository;

import mall.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username); // username을 기반으로 User table 탐색

	boolean existsByUsername(String username);

	Optional<User> findByUserId(String userId);
}