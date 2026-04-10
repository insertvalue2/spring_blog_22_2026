package org.example.demo_ssr_v1_1.reply;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

/**
 * Reply 도메인 응답 DTO 묶음.
 *
 * [왜 DTO 를 쓰는가?]
 *  · OSIV false 환경에서 트랜잭션 종료 후 Lazy 필드 접근을 막기 위해
 *  · 화면이 엔티티 구조에 의존하지 않도록 떼어내기 위해
 */
public class ReplyResponse {

    /**
     * 댓글 목록용 DTO
     *
     * [포함 필드]
     *  · id/comment/createdAt : 화면 표시용
     *  · userId/username      : 작성자 표시용
     *  · isOwner              : "삭제" 버튼을 보여줄지 결정하는 플래그
     */
    @Data
    public static class ListDTO {
        private Long id;
        private String comment;
        private Long userId;
        private String username;
        private String createdAt;
        private boolean isOwner;

        public ListDTO(Reply reply, Long sessionUserId) {
            // 1. 기본 필드 복사
            this.id = reply.getId();
            this.comment = reply.getComment();

            // 2. 작성자 정보 (JOIN FETCH 로 로딩돼 있어야 한다)
            if (reply.getUser() != null) {
                this.userId = reply.getUser().getId();
                this.username = reply.getUser().getUsername();
            }

            // 3. 날짜 포맷팅
            if (reply.getCreatedAt() != null) {
                this.createdAt = MyDateUtil.timestampFormat(reply.getCreatedAt());
            }

            // 4. 소유자 여부 계산 (세션 사용자와 댓글 작성자 비교)
            this.isOwner = reply.isOwner(sessionUserId);
        }
    }
}
