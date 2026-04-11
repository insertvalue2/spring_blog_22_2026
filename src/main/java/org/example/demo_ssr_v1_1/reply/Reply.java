package org.example.demo_ssr_v1_1.reply;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1.board.Board;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

/**
 * Reply (댓글) 엔티티
 *
 * [관계 설계 - 단방향 @ManyToOne]
 *  · Reply → Board (N:1) : 댓글이 속한 게시글
 *  · Reply → User  (N:1) : 댓글 작성자
 *  · 학습용 단순화를 위해 양방향 매핑은 사용하지 않는다.
 *    (양방향을 쓰면 toString/equals 무한루프, 영속성 전이 고민이 늘어난다)
 */
@Data
@NoArgsConstructor
@Table(name = "reply_tb")
@Entity
public class Reply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 댓글 본문 (최대 500자) */
    @Column(length = 500)
    private String comment;

    /** 댓글이 달린 게시글 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    /** 댓글 작성자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    private Timestamp createdAt;

    @Builder
    public Reply(String comment, Board board, User user) {
        this.comment = comment;
        this.board = board;
        this.user = user;
    }

    /**
     * 댓글 소유자 확인.
     *
     * [동작 흐름]
     *  1) user 또는 userId 가 null 이면 false (아무도 소유자가 아님)
     *  2) 참조 엔티티의 id 와 비교
     *  3) Long 객체 비교는 반드시 equals() 를 사용 (== 는 레퍼런스 비교라 위험)
     */
    public boolean isOwner(Long userId) {
        if (this.user == null || userId == null) {
            return false;
        }
        Long replyUserId = this.user.getId();
        if (replyUserId == null) {
            return false;
        }
        return replyUserId.equals(userId);
    }

    /**
     * 댓글 내용 수정.
     *
     * [동작 흐름]
     *  1) 빈 값 거부
     *  2) 500자 초과 거부
     *  3) comment 필드 덮어쓰기 (더티 체킹 기대)
     */
    public void update(String newComment) {
        if (newComment == null || newComment.trim().isEmpty()) {
            throw new Exception400("댓글 내용은 필수입니다");
        }
        if (newComment.length() > 500) {
            throw new Exception400("댓글은 500자 이하여야 합니다");
        }
        this.comment = newComment;
    }
}
