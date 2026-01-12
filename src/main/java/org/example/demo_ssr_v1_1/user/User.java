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

// 엔티티 화면 보고 설계해 보세요.
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

    // 프로필 이미지 파일명 (선택사항)
    private String profileImage;

    // 포인트 (기본값 0)
    @ColumnDefault("0")
    private Integer point = 0;


    // 리스트는 절대 null이 아니도록 초기화
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private List<UserRole> roles = new ArrayList<>();

    @CreationTimestamp
    private Timestamp createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) // null 허용 안 함 (필수)
    @ColumnDefault("'LOCAL'") // 문자열이므로 작은따옴표 필수!
    private OAuthProvider provider;

    @Builder
    public User(Long id, String username, String password, String email,
                String profileImage, Timestamp createdAt, List<UserRole> roles,
                OAuthProvider provider, Integer point) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.profileImage = profileImage;
        this.createdAt = createdAt;
        this.provider = provider;
        this.point = (point != null) ? point : 0;

        // roles가 없거나 비어있으면 -> 'USER' 권한 강제 주입
        // 빌더로 roles를 안 넣으면 null이 들어오므로 체크해야 함
        this.roles = (roles != null) ? roles : new ArrayList<>();

        if (this.roles.isEmpty()) {
            this.roles.add(UserRole.builder().role(Role.USER).build());
        }

        // provider가 없으면(null) -> 'LOCAL'로 설정
        if (provider == null) {
            this.provider = OAuthProvider.LOCAL;
        } else {
            this.provider = provider;
        }

    }

    // 회원정보 수정 비즈니스 로직 추가
    // 추후 DTO  설계
    public void update(UserRequest.UpdateDTO updateDTO) {
        // 유효성 검사
        updateDTO.validate();
        this.password = updateDTO.getPassword();
        // 프로필 이미지 파일명이 있으면 업데이트 (null이면 기존 이미지 유지)
        if (updateDTO.getProfileImageFilename() != null) {
            this.profileImage = updateDTO.getProfileImageFilename();
        }
        // 더티 체킹 (변경 감지)
        // 트랜잭션이 끝나면 자동으로 update 쿼리 진행
    }

    // 회원정보 소유자 확인 로직
    public boolean isOwner(Long userId) {
        if (this.id == null || userId == null) {
            return false;
        }
        return this.id.equals(userId);
    }

    // ===================== 권한(ROLE) 관련 편의 메서드 =====================

    /**
     * 새로운 역할을 추가합니다.
     * 최초 회원가입 시 기본 USER 역할을 부여하거나,
     * 관리자가 ADMIN 역할을 부여할 때 사용할 수 있습니다.
     */
    public void addRole(Role role) {
        this.roles.add(UserRole.builder().role(role).build());
    }

    /**
     * 해당 역할을 가지고 있는지 확인합니다.
     */
    public boolean hasRole(Role role) {
        // roles 컬렉션이 없거나 비어 있으면 해당 역할이 없는 것
        if (this.roles == null || this.roles.isEmpty()) {
            return false;
        }
        // EAGER 로딩을 사용하므로 단순히 stream으로 검사해도 됨
        return this.roles.stream()
                .anyMatch(r -> r.getRole() == role);
    }

    /**
     * 관리자인지 여부를 반환합니다.
     */
    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Mustache에서 boolean 프로퍼티로 접근하기 위한 getter.
     * - 템플릿에서 {{#isAdmin}} ... {{/isAdmin}} 형태로 사용할 수 있습니다.
     */
    public boolean getIsAdmin() {
        return isAdmin();
    }

    /**
     * 화면에 표시할 역할 문자열.
     * - ADMIN 이면 "ADMIN"
     * - 그 외(또는 기본)는 "USER"
     */
    public String getRoleDisplay() {
        return isAdmin() ? "ADMIN" : "USER";
    }

    public boolean isLocal() {
        return this.provider == OAuthProvider.LOCAL;
    }

    /**
     * [이미지 경로 반환 로직]
     * - 소셜 로그인(http로 시작): URL 그대로 반환
     * - 로컬 로그인(파일명): /images/ 경로를 붙여서 반환
     * - 이미지 없음: null 반환
     */
    public String getProfilePath() {
        if (this.profileImage == null) {
            return null;
        }
        // http 로 시작하면(소셜 이미지) 그대로 리턴
        if (this.profileImage.startsWith("http")) {
            return this.profileImage;
        }
        // 아니면(로컬 이미지) 폴더 경로 붙여서 리턴
        return "/images/" + this.profileImage;
    }

    // ===================== 포인트 관련 편의 메서드 =====================

    /**
     * 포인트를 차감합니다.
     * 
     * @param amount 차감할 포인트
     * @throws Exception400 포인트가 부족한 경우
     */
    public void deductPoint(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new org.example.demo_ssr_v1_1._core.errors.exception.Exception400("차감할 포인트는 0보다 커야 합니다");
        }
        if (this.point == null) {
            this.point = 0;
        }
        if (this.point < amount) {
            throw new org.example.demo_ssr_v1_1._core.errors.exception.Exception400("포인트가 부족합니다. 현재 포인트: " + this.point);
        }
        this.point -= amount;
    }

    /**
     * 포인트를 충전합니다.
     * 
     * @param amount 충전할 포인트
     */
    public void chargePoint(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new org.example.demo_ssr_v1_1._core.errors.exception.Exception400("충전할 포인트는 0보다 커야 합니다");
        }
        if (this.point == null) {
            this.point = 0;
        }
        this.point += amount;
    }

}
