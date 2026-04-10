package org.example.demo_ssr_v1_1.reply;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Reply 엔티티 Repository
 *
 * [핵심 개념]
 *  1) JpaRepository 기본 메서드(save, findById, delete ...) 를 그대로 사용할 수 있다.
 *  2) 상세/목록 조회는 JOIN FETCH 로 작성자(User) 와 게시글(Board) 을 한 번에 가져와 N+1 을 피한다.
 *  3) deleteByBoardId 는 Spring Data JPA 쿼리 메서드 규칙으로 자동 생성된다.
 */
@Repository
public interface ReplyRepository extends JpaRepository<Reply, Long> {

    /**
     * 게시글 ID 로 댓글 목록 조회 (작성자 + 게시글 함께 로딩).
     *
     * 실행 SQL (의사 코드) :
     *   SELECT r.*, u.*, b.*
     *   FROM reply_tb r
     *   INNER JOIN user_tb u  ON r.user_id  = u.id
     *   INNER JOIN board_tb b ON r.board_id = b.id
     *   WHERE r.board_id = :boardId
     *   ORDER BY r.created_at ASC
     */
    @Query("SELECT r FROM Reply r "
            + "JOIN FETCH r.user "
            + "JOIN FETCH r.board "
            + "WHERE r.board.id = :boardId "
            + "ORDER BY r.createdAt ASC")
    List<Reply> findByBoardIdWithUser(@Param("boardId") Long boardId);

    /**
     * 댓글 ID 로 단건 조회 (작성자 + 게시글 포함).
     */
    @Query("SELECT r FROM Reply r "
            + "JOIN FETCH r.user "
            + "JOIN FETCH r.board "
            + "WHERE r.id = :id")
    Optional<Reply> findByIdWithUser(@Param("id") Long id);

    /**
     * 게시글 ID 로 해당 게시글의 모든 댓글을 삭제.
     *
     * [왜 필요한가?]
     *  게시글을 삭제할 때, reply_tb 의 FK(board_id) 제약 때문에
     *  자식(댓글) 을 먼저 지워야 부모(게시글) 를 지울 수 있다.
     *
     * [주의 - 벌크 삭제 쿼리]
     *  이 메서드는 내부적으로 DELETE ... WHERE board_id = ? 를 한 번에 실행한다.
     *  영속성 컨텍스트의 1차 캐시와 어긋날 수 있으므로, 학습용으로는
     *  "게시글 삭제 트랜잭션의 맨 앞에서만" 호출한다고 생각하면 된다.
     */
    void deleteByBoardId(Long boardId);
}
