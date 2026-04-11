package org.example.demo_ssr_v1_1.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Purchase 엔티티 Repository
 *
 * [주요 쿼리]
 *  · (userId, boardId) 로 단건 조회 → "이미 구매했는가?" 판정
 *  · 사용자 구매 내역 전체 조회 (+ Board, User JOIN FETCH)
 */
@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    /**
     * 특정 사용자가 특정 게시글을 이미 구매했는지 확인하기 위해 조회.
     */
    @Query("SELECT p FROM Purchase p WHERE p.user.id = :userId AND p.board.id = :boardId")
    Optional<Purchase> findByUserIdAndBoardId(@Param("userId") Long userId,
                                              @Param("boardId") Long boardId);

    /**
     * 구매 여부만 간단하게 확인.
     *
     * [디폴트 메서드]
     *  · JpaRepository 는 인터페이스지만, default 키워드로 간단한 로직을 넣을 수 있다.
     *  · 여기서는 findByUserIdAndBoardId 를 재활용해 "present 여부" 만 반환한다.
     */
    default boolean existsByUserIdAndBoardId(Long userId, Long boardId) {
        return findByUserIdAndBoardId(userId, boardId).isPresent();
    }

    /**
     * 특정 사용자의 구매 내역 목록을 최신순으로 조회한다.
     * 상세 조회 시 board, user 까지 함께 써야 하므로 JOIN FETCH 한다.
     */
    @Query("""
            SELECT p FROM Purchase p
            JOIN FETCH p.board b
            JOIN FETCH p.user u
            WHERE p.user.id = :userId
            ORDER BY p.createdAt DESC
            """)
    List<Purchase> findAllByUserIdWithBoard(@Param("userId") Long userId);
}
