package org.example.demo_ssr_v1_1.board;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1.user.User;

/**
 * Board 도메인의 "요청(Request) DTO" 묶음
 *
 * [이 클래스의 역할]
 *  · Controller 가 HTTP 요청 파라미터를 이 DTO 로 받는다.
 *  · DTO 내부에 validate() 를 두어 "형식 검증" 을 한곳에서 처리한다.
 *  · toEntity() 를 두어 DTO → Entity 변환 책임을 DTO 쪽에 둔다.
 */
public class BoardRequest {

    // =========================================================================
    // 게시글 저장
    // =========================================================================
    @Data
    public static class SaveDTO {
        private String title;
        private String content;
        private String username;     // 폼에서 참고 용으로만 사용 (실제 작성자는 세션 User)
        private Boolean premium;     // 유료 게시글 여부 (체크박스)

        /**
         * DTO → Entity 변환.
         *
         * @param user 로그인 세션에서 꺼낸 작성자 엔티티 (Service 에서 주입)
         */
        public Board toEntity(User user) {
            return Board.builder()
                    .title(title)
                    .content(content)
                    .user(user)
                    // premium 은 null 가능성이 있으므로 false 로 기본화
                    .premium(premium != null ? premium : false)
                    .build();
        }
    }

    // =========================================================================
    // 게시글 수정
    // =========================================================================
    @Data
    public static class UpdateDTO {
        private String title;
        private String content;
        private String username;
        private Boolean premium;

        public void validate() {
            if (title == null || title.trim().isEmpty()) {
                throw new Exception400("제목은 필수 입니다");
            }
            if (content == null || content.trim().isEmpty()) {
                throw new Exception400("내용은 필수 입니다");
            }
        }
    }
}
