package org.example.demo_ssr_v1_1.user;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * User 도메인의 "응답(Response) DTO" 묶음 클래스.
 *
 * [왜 엔티티 대신 DTO 를 반환하는가?]
 * 1. OSIV(Open Session In View) 가 false 이므로, 트랜잭션이 끝난 뒤
 *    뷰에서 엔티티를 만지면 LazyInitializationException 이 날 수 있다.
 * 2. 엔티티에는 password, roles 같은 민감/비대용 필드가 있어서 뷰/외부 API 에 그대로 노출하면 안 된다.
 * 3. 계층 간 결합을 끊어준다 (Service ↔ Controller ↔ View 가 엔티티 모양에 의존하지 않음).
 */
public class UserResponse {

    // =========================================================================
    // 회원정보 수정 폼에 내려보낼 DTO
    // =========================================================================
    @Data
    public static class UpdateFormDTO {
        private Long id;
        private String username;
        private String email;

        public UpdateFormDTO(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.email = user.getEmail();
        }
    }

    // =========================================================================
    // 로그인 응답 / 세션 저장용 DTO
    // =========================================================================
    @Data
    public static class LoginDTO {
        private Long id;
        private String username;
        private String email;

        public LoginDTO(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.email = user.getEmail();
        }
    }

    // =========================================================================
    // 카카오 OAuth 응답 모델
    // =========================================================================

    /**
     * 카카오 토큰 엔드포인트 응답.
     *
     * [@JsonNaming]
     * 카카오는 JSON 필드명을 snake_case("access_token") 로 주고,
     * 자바는 camelCase("accessToken") 가 관례이므로 자동 변환을 걸어둔다.
     */
    @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Data
    public static class OAuthToken {
        private String tokenType;            // "bearer" 고정
        private String accessToken;          // 이후 프로필 조회 API 호출 시 사용할 토큰
        private Integer expiresIn;           // 액세스 토큰 만료(초)
        private String refreshToken;         // 재발급용 토큰
        private String refreshTokenExpiresIn;// 재발급 토큰 만료(초)
    }

    /**
     * 카카오 사용자 정보 응답.
     */
    @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Data
    public static class KakaoProfile {
        private Long id;                     // 카카오 회원 번호 (고유)
        private String connectedAt;          // 서비스 연결 시각
        private Properties properties;       // 닉네임/프로필 이미지 등
    }

    /**
     * 카카오 프로필 properties 하위 필드.
     */
    @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Data
    public static class Properties {
        private String nickname;
        private String profileImage;
        private String thumbnailImage;
    }
}
