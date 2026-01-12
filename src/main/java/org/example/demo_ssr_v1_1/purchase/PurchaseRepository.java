package org.example.demo_ssr_v1_1.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 구매 내역 Repository 인터페이스
 * 
 * User와 Board의 구매 관계를 관리합니다.
 */
@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    /**
     * 사용자와 게시글의 구매 내역 조회
     * 
     * @param userId 사용자 ID
     * @param boardId 게시글 ID
     * @return 구매 내역 (Optional)
     */
    @Query("SELECT p FROM Purchase p WHERE p.user.id = :userId AND p.board.id = :boardId")
    Optional<Purchase> findByUserIdAndBoardId(@Param("userId") Long userId, @Param("boardId") Long boardId);

    /**
     * 사용자와 게시글의 구매 여부 확인
     * 
     * @param userId 사용자 ID
     * @param boardId 게시글 ID
     * @return 구매 여부
     */
    default boolean existsByUserIdAndBoardId(Long userId, Long boardId) {
        return findByUserIdAndBoardId(userId, boardId).isPresent();
    }

    //  조인 패치 사용
    @Query("""
        SELECT p FROM Purchase p
        JOIN FETCH p.board b 
        JOIN FETCH p.user u 
        WHERE p.user.id = :userId 
        ORDER BY p.createdAt DESC """)
    List<Purchase> findAllByUserIdWithBoard(@Param("userId") Long userId);
}
