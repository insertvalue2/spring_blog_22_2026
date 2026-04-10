package org.example.demo_ssr_v1_1.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RefundRequest 엔티티 Repository
 *
 * [쿼리 전략]
 *  · 목록/상세 조회는 JOIN FETCH 로 Payment 와 User 까지 한 번에 로딩한다.
 *  · 환불 화면에는 "누가, 어떤 결제를, 얼마나" 가 같이 표시돼야 하기 때문.
 *  · findByPaymentId 는 단순한 중복 검사용이라 JOIN FETCH 없이 둔다.
 */
@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    /**
     * 특정 사용자의 환불 요청 목록 (최신순).
     * 화면: 마이페이지 "내 환불 목록"
     */
    @Query("""
            SELECT r FROM RefundRequest r
            JOIN FETCH r.payment p
            JOIN FETCH p.user u
            WHERE r.user.id = :userId
            ORDER BY r.createdAt DESC
            """)
    List<RefundRequest> findAllByUserId(@Param("userId") Long userId);

    /**
     * 전체 환불 요청 목록 (최신순).
     * 화면: 관리자 "전체 환불 관리" (상태 무관)
     */
    @Query("""
            SELECT r FROM RefundRequest r
            JOIN FETCH r.payment p
            JOIN FETCH r.user u
            ORDER BY r.createdAt DESC
            """)
    List<RefundRequest> findAllWithUserAndPayment();

    /**
     * 대기 중(PENDING) 환불 요청 목록 (최신순).
     * 화면: 관리자 "처리할 건만" 보기
     */
    @Query("""
            SELECT r FROM RefundRequest r
            JOIN FETCH r.payment p
            JOIN FETCH r.user u
            WHERE r.status = 'PENDING'
            ORDER BY r.createdAt DESC
            """)
    List<RefundRequest> findAllPending();

    /**
     * id 로 단건 조회 + User + Payment 함께 로딩.
     * 화면: 관리자 승인/거절 처리 직전.
     */
    @Query("""
            SELECT r FROM RefundRequest r
            JOIN FETCH r.payment p
            JOIN FETCH r.user u
            WHERE r.id = :id
            """)
    Optional<RefundRequest> findByIdWithUserAndPayment(@Param("id") Long id);

    /**
     * paymentId 로 환불 요청 조회.
     * 용도: "이 결제에 이미 환불 요청이 걸려 있는가?" 를 빠르게 확인.
     */
    Optional<RefundRequest> findByPaymentId(Long paymentId);
}
