package org.example.demo_ssr_v1_1.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Board 엔티티 Repository
 *
 * [핵심 개념]
 *  1. JpaRepository&lt;Board, Long&gt; 상속만으로
 *     save / findById / findAll / deleteById / count / existsById 등이 자동으로 제공된다.
 *  2. 복잡한 조회는 @Query 로 JPQL 을 직접 작성한다.
 *  3. JOIN FETCH 는 연관 엔티티를 "한 방 쿼리" 로 가져와 N+1 을 막는다.
 *     단, Page 반환과 함께 쓸 수 없으므로 페이징 시에는 일반 JOIN + 배치 페치를 사용한다.
 *
 * [학습 포인트 - @Lob 컬럼과 LIKE 검색]
 *  content 컬럼은 @Lob 이므로 Hibernate 에서 CLOB 로 매핑된다.
 *  CLOB 에 LOWER() 같은 String 함수를 쓰면 "타입 불일치" 오류가 나므로 주의.
 *  LIKE 만 사용하는 지금의 쿼리는 안전하다.
 */
@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

    /**
     * 상세 조회 - 게시글 + 작성자(User) 를 한 번에 가져온다. (JOIN FETCH)
     *
     * 실행 SQL (의사 코드) :
     *   SELECT b.*, u.*
     *   FROM board_tb b INNER JOIN user_tb u ON b.user_id = u.id
     *   WHERE b.id = :id
     */
    @Query("SELECT b FROM Board b JOIN FETCH b.user WHERE b.id = :id")
    Optional<Board> findByIdWithUser(@Param("id") Long id);

    /**
     * 목록 페이징 조회 - 작성자 정보 포함 (일반 JOIN + 배치 페치)
     *
     * [왜 JOIN FETCH 가 아닌 일반 JOIN 인가?]
     *  · Hibernate 는 "JOIN FETCH + Pageable" 조합을 허용하지 않는다.
     *    (메모리에서 페이징하게 되면 경고 + 성능 문제)
     *  · 대신 application.yml 의 default_batch_fetch_size 로 N+1 을 완화한다.
     */
    @Query("SELECT b FROM Board b JOIN b.user ORDER BY b.createdAt DESC")
    Page<Board> findAllWithUserOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 검색 페이징 조회 - 제목 OR 본문 LIKE
     *
     * [SQL Injection 안전한 이유]
     *  · :keyword 를 @Param 으로 바인딩하므로 사용자 입력이 SQL 구문 자체로 해석되지 않는다.
     *  · 절대 "... LIKE '%" + keyword + "%'" 처럼 문자열 연결하지 말 것.
     */
    @Query("SELECT b FROM Board b JOIN b.user "
            + "WHERE b.title LIKE CONCAT('%', :keyword, '%') "
            + "   OR b.content LIKE CONCAT('%', :keyword, '%') "
            + "ORDER BY b.createdAt DESC")
    Page<Board> findByTitleContainingOrContentContaining(@Param("keyword") String keyword, Pageable pageable);
}
