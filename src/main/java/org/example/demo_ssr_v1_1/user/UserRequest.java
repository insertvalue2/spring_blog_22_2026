package org.example.demo_ssr_v1_1.user;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.springframework.web.multipart.MultipartFile;

/**
 * User 도메인의 "요청(Request) DTO" 묶음 클래스.
 *
 * [왜 static inner class 로 묶는가?]
 * - LoginDTO, JoinDTO, UpdateDTO 처럼 한 도메인에 속하는 DTO 들이 많을 때
 *   파일을 N개로 쪼개는 것보다 "UserRequest" 하나로 묶어두면 탐색이 편하다.
 * - 호출 코드에서 UserRequest.LoginDTO 로 네임스페이스가 드러나 가독성이 좋아진다.
 */
public class UserRequest {

    // =========================================================================
    // 로그인
    // =========================================================================
    @Data
    public static class LoginDTO {
        private String username;
        private String password;

        public void validate() {
            if (username == null || username.trim().isEmpty()) {
                throw new Exception400("사용자명을 입력해주세요");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new Exception400("비밀번호를 입력해주세요");
            }
        }
    }

    // =========================================================================
    // 회원가입
    // =========================================================================
    @Data
    public static class JoinDTO {
        private String username;
        private String password;
        private String email;

        /**
         * 프로필 이미지 (선택)
         *
         * [MultipartFile 이란?]
         * - Spring 에서 HTML form 의 &lt;input type="file"&gt; 로 올라온 파일을 받는 타입.
         * - enctype="multipart/form-data" 로 전송된 요청에 한해 자동 바인딩된다.
         *
         * [주의]
         * - DB 에는 MultipartFile 을 그대로 저장할 수 없다.
         * - Service 에서 FileUtil.saveFile() 로 디스크에 저장한 뒤, "파일명" 만 User 엔티티에 넣는다.
         */
        private MultipartFile profileImage;

        public void validate() {
            if (username == null || username.trim().isEmpty()) {
                throw new Exception400("사용자명을 입력해주세요");
            }
            if (password == null || password.trim().isEmpty()) {
                throw new Exception400("비밀번호를 입력해주세요");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new Exception400("이메일을 입력해주세요");
            }
            if (!email.contains("@")) {
                throw new Exception400("올바른 이메일 형식이 아닙니다");
            }
            // 프로필 이미지는 선택 사항이므로 검증하지 않는다.
        }

        /**
         * DTO -> Entity 변환.
         *
         * @param profileImageFilename Service 에서 파일을 저장한 뒤 돌려준 파일명 (없으면 null)
         */
        public User toEntity(String profileImageFilename) {
            return User.builder()
                    .username(this.username)
                    .password(this.password)           // 평문 상태. Service 에서 BCrypt 해싱 후 덮어씀.
                    .email(this.email)
                    .profileImage(profileImageFilename) // 파일명만 저장 (실제 파일은 images/ 디렉터리)
                    .build();
        }
    }

    // =========================================================================
    // 회원정보 수정
    // =========================================================================
    @Data
    public static class UpdateDTO {
        private String password;
        private MultipartFile profileImage;    // 새 프로필 이미지 (선택)
        private String profileImageFilename;   // Service 에서 저장 후 세팅할 파일명
        // username 은 수정 불가능한 "고유 식별자" 이므로 필드에 없음.

        public void validate() {
            if (password == null || password.trim().isEmpty()) {
                throw new Exception400("비밀번호를 입력해주세요");
            }
            if (password.length() < 4) {
                throw new Exception400("비밀번호는 4글자 이상이어야 합니다");
            }
            // 프로필 이미지는 선택 사항이므로 검증하지 않는다.
        }
    }

    // =========================================================================
    // 이메일 인증
    // =========================================================================

    /**
     * 이메일 인증 DTO
     *
     * [사용 시나리오]
     *   A. 인증번호 발송 (/api/email/send) : email 만 사용, code 는 null
     *   B. 인증번호 확인 (/api/email/verify) : email + code 둘 다 사용
     */
    @Data
    public static class EmailCheckDTO {
        private String email;
        private String code;

        public void validate() {
            if (email == null || email.trim().isEmpty()) {
                throw new Exception400("이메일을 입력해주세요");
            }
            if (!email.contains("@")) {
                throw new Exception400("올바른 이메일 형식이 아닙니다");
            }
        }
    }

    // =========================================================================
    // 포인트 충전 (테스트용)
    // =========================================================================
    @Data
    public static class PointChargeDTO {
        private Integer amount;

        public void validate() {
            if (amount == null || amount <= 0) {
                throw new Exception400("충전할 포인트는 0보다 커야 합니다");
            }
        }
    }
}
