package org.example.demo_ssr_v1_1.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * User 엔티티 (user_tb 테이블과 매핑)
 *
 * [학습 포인트]
 *
 * 1. @Entity 와 @Table
 *    - 해당 클래스가 JPA 가 관리하는 "영속 엔티티" 임을 선언한다.
 *    - @Table(name="user_tb") 로 실제 테이블명을 지정한다 (기본값은 클래스명).
 *
 * 2. @Data 사용 주의
 *    - Lombok @Data 는 Setter 를 전부 열어주기 때문에,
 *      실전 코드에서는 엔티티에는 쓰지 않는 편이 안전하다.
 *    - 이 프로젝트는 "학습용"이므로 편의상 사용. 대신 수정 로직은 가능하면
 *      update(...) 같은 "의미 있는 메서드" 로만 바꾸도록 연습한다.
 *
 * 3. @ColumnDefault
 *    - DDL 생성 시 컬럼 기본값을 지정한다. (INSERT 생략 시 DB 쪽에서 채워준다)
 *    - 자바 필드 초기값(= 0, = false) 과는 별개의 개념이므로 둘 다 두는 것이 안전하다.
 *
 * 4. @OneToMany(roles)
 *    - 한 사용자가 여러 역할(UserRole) 을 가질 수 있다.
 *    - fetch = EAGER 는 학습용 단순화를 위해 유지한다.
 *      실전에서는 LAZY + JOIN FETCH 패턴을 권장한다(보안.md 참조).
 *    - cascade=ALL + orphanRemoval=true : 사용자 삭제 시 역할도 함께 정리된다.
 */
@NoArgsConstructor
@Data
@Table(name = "user_tb")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;

    @Column(unique = true)
    private String email;

    /** 프로필 이미지 파일명(로컬 업로드) 또는 URL(소셜 로그인). 선택 사항. */
    private String profileImage;

    /** 보유 포인트 (기본값 0). */
    @ColumnDefault("0")
    private Integer point = 0;

    /**
     * 사용자의 역할(권한) 목록.
     *
     * [관계 매핑 설명]
     * - @OneToMany : User : UserRole = 1 : N
     * - @JoinColumn(name="user_id") : UserRole 테이블에 user_id 라는 FK 컬럼을 둔다.
     *
     * [주의 - null 금지]
     * - 컬렉션 필드는 "비어 있을 수 있다" 지만 "null 이어서는 안 된다".
     * - 새 ArrayList 로 초기화해두는 것이 안전한 관례이다.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private List<UserRole> roles = new ArrayList<>();

    @CreationTimestamp
    private Timestamp createdAt;

    /**
     * 로그인 제공자(LOCAL / KAKAO / ...)
     *
     * @Enumerated(STRING)
     *   - enum 을 DB 에 "문자열" 로 저장한다.
     *   - 기본값 ORDINAL 은 순서가 바뀌면 DB 데이터가 어긋나므로 절대 쓰지 말 것.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'LOCAL'")
    private OAuthProvider provider;

    // =========================================================================
    // 빌더
    // =========================================================================

    @Builder
    public User(Long id, String username, String password, String email,
                String profileImage, Timestamp createdAt, List<UserRole> roles,
                OAuthProvider provider, Integer point) {
        // 1. 기본 필드 세팅
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.profileImage = profileImage;
        this.createdAt = createdAt;

        // 2. point 는 null 이면 0 으로 보정
        this.point = (point != null) ? point : 0;

        // 3. roles 가 없으면 빈 리스트로 시작, 그 뒤에 기본 USER 역할을 1개 넣는다.
        //    -> 회원가입 시 누구든 최소한 "USER" 권한을 가지도록 보장
        this.roles = (roles != null) ? roles : new ArrayList<>();
        if (this.roles.isEmpty()) {
            this.roles.add(UserRole.builder().role(Role.USER).build());
        }

        // 4. provider 는 null 이면 LOCAL 로 보정
        this.provider = (provider != null) ? provider : OAuthProvider.LOCAL;
    }

    // =========================================================================
    // 상태 변경 로직 (도메인 메서드)
    // =========================================================================

    /**
     * 회원정보 수정.
     *
     * [동작 흐름]
     * 1) DTO 자체 검증 (password 길이 등)
     * 2) 비밀번호 필드 덮어쓰기 (※ Service 단에서 BCrypt 해싱한 후 넘긴 값이어야 함)
     * 3) 프로필 이미지 파일명이 있으면 교체, 없으면 기존 값 유지
     *
     * [더티 체킹]
     * - @Transactional 범위 안에서 이 메서드를 호출하면,
     *   트랜잭션 커밋 시 Hibernate 가 자동으로 UPDATE 쿼리를 날려준다.
     * - save() 를 명시적으로 부르지 않아도 되는 이유가 여기에 있다.
     */
    public void update(UserRequest.UpdateDTO updateDTO) {
        updateDTO.validate();
        this.password = updateDTO.getPassword();
        if (updateDTO.getProfileImageFilename() != null) {
            this.profileImage = updateDTO.getProfileImageFilename();
        }
    }

    /**
     * 본인 확인 (소유권 검사).
     *
     * [왜 필요한가?]
     * - "URL 의 id" 를 조작해 다른 사람 정보를 수정/삭제하는 공격(IDOR)을 막기 위해,
     *   Service 에서 항상 "이 엔티티의 주인이 지금 로그인한 사용자인가?" 를 검사한다.
     */
    public boolean isOwner(Long userId) {
        if (this.id == null || userId == null) {
            return false;
        }
        return this.id.equals(userId);
    }

    // =========================================================================
    // 권한 관련 편의 메서드
    // =========================================================================

    /** 역할 추가 (예: 관리자가 승격을 시키는 경우) */
    public void addRole(Role role) {
        this.roles.add(UserRole.builder().role(role).build());
    }

    /** 특정 역할 보유 여부 확인 */
    public boolean hasRole(Role role) {
        if (this.roles == null || this.roles.isEmpty()) {
            return false;
        }
        return this.roles.stream().anyMatch(r -> r.getRole() == role);
    }

    /** 관리자인지 */
    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Mustache 에서 {{#isAdmin}}...{{/isAdmin}} 형태로 사용할 수 있도록 하는 getter.
     *
     * [왜 이게 필요한가?]
     * - Mustache 는 bean getter 규칙(getXxx / isXxx) 을 통해 필드에 접근한다.
     * - isAdmin() 는 이미 "메서드" 로 존재하지만, 섹션 태그에서 잘 먹히려면
     *   get 으로 시작하는 getter 를 제공하는 편이 호환성이 좋다.
     */
    public boolean getIsAdmin() {
        return isAdmin();
    }

    /**
     * 화면에 노출할 역할 문자열.
     * ADMIN 이면 "ADMIN", 아니면 "USER" 로 단순화.
     */
    public String getRoleDisplay() {
        return isAdmin() ? "ADMIN" : "USER";
    }

    /** LOCAL(자체) 가입자인지 여부 */
    public boolean isLocal() {
        return this.provider == OAuthProvider.LOCAL;
    }

    /**
     * 화면에서 쓸 프로필 이미지 경로.
     *
     * [분기 이유]
     * - LOCAL 사용자는 우리가 직접 받아 저장한 파일명이 저장돼 있으므로 /images/ prefix 를 붙인다.
     * - 소셜(카카오) 사용자는 외부 URL(http...) 이 저장돼 있으므로 그대로 반환한다.
     */
    public String getProfilePath() {
        if (this.profileImage == null) {
            return null;
        }
        if (this.profileImage.startsWith("http")) {
            return this.profileImage;
        }
        return "/images/" + this.profileImage;
    }

    // =========================================================================
    // 포인트 관련 편의 메서드
    // =========================================================================

    /**
     * 포인트 차감.
     *
     * [학습 포인트]
     * 1) 입력값 검증 : 0 이하 금액은 의미 없는 호출이므로 400 예외.
     * 2) 포인트가 null 이면 0 으로 보정 (방어 코드)
     * 3) 잔액 부족이면 400 예외
     * 4) 차감 수행
     */
    public void deductPoint(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new Exception400("차감할 포인트는 0보다 커야 합니다");
        }
        if (this.point == null) {
            this.point = 0;
        }
        if (this.point < amount) {
            throw new Exception400("포인트가 부족합니다. 현재 포인트: " + this.point);
        }
        this.point -= amount;
    }

    /**
     * 포인트 충전.
     * - 음수/0 금액 거부
     * - null 이면 0 으로 보정 후 덧셈
     */
    public void chargePoint(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new Exception400("충전할 포인트는 0보다 커야 합니다");
        }
        if (this.point == null) {
            this.point = 0;
        }
        this.point += amount;
    }
}
