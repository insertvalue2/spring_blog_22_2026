package org.example.demo_ssr_v1_1.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User 엔티티 Repository
 *
 * [핵심 개념]
 * 1. JpaRepository&lt;User, Long&gt; 를 상속하면
 *    - save, findById, findAll, deleteById 등이 자동으로 생긴다.
 * 2. 메서드 이름만으로 쿼리를 만들어주는 "쿼리 메서드" 기능을 지원한다.
 *    findByUsername(String) -> SELECT * FROM user_tb WHERE username = ?
 * 3. 복잡한 쿼리는 @Query 로 직접 JPQL 을 쓴다.
 *
 * [반환 타입 선택]
 * - Optional&lt;T&gt; : "결과가 없을 수도 있음" 을 타입으로 드러내기 위해 사용.
 *   isPresent() / orElseThrow() 등을 통해 null 안전성을 높인다.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * username 으로 사용자 1명 조회.
     * 실행 SQL : SELECT * FROM user_tb WHERE username = ?
     */
    Optional<User> findByUsername(String username);

    /**
     * email 로 사용자 1명 조회. (회원가입 중복 체크용)
     * 실행 SQL : SELECT * FROM user_tb WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * 로그인 시 사용자 + 권한(roles) 을 한 번의 쿼리로 함께 가져온다.
     *
     * [왜 JOIN FETCH 인가?]
     * - user.roles 는 @OneToMany 컬렉션이다. 기본 전략(LAZY) 이면
     *   User 를 조회한 뒤 roles 에 접근하는 순간 추가 쿼리가 나간다(N+1).
     * - JOIN FETCH 를 쓰면 roles 까지 "한 번의 쿼리" 로 로딩한다.
     *
     * [distinct 가 필요한 이유]
     * - 1:N JOIN 에서는 User 가 roles 개수만큼 중복돼서 결과에 나타난다.
     * - distinct 로 중복 제거 (하이버네이트가 내부적으로 걸러준다).
     *
     * [비밀번호는 여기서 비교하지 않는다]
     * - BCrypt 해시는 평문과 직접 비교가 불가능하다.
     * - 반드시 Service 에서 passwordEncoder.matches(입력평문, 저장해시) 로 검증한다.
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);
}
