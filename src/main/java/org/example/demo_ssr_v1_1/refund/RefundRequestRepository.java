package org.example.demo_ssr_v1_1.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 환불 요청 Repository
 */
@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    /**
     * 사용자 ID로 환불 요청 목록 조회 (최신순)
     * [용도] 사용자가 "내 환불 내역" 화면에서 자신이 요청한 환불 목록을 확인할 때 사용합니다.
     * JOIN FETCH를 사용하여 N+1 문제 방지
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
     * 전체 환불 요청 목록 조회 (관리자용, 최신순)
     * [용도] 관리자가 "전체 환불 관리" 화면에서 모든 사용자의 환불 요청(대기, 승인, 거절 포함)을 한눈에 볼 때 사용합니다.
     * JOIN FETCH를 사용하여 N+1 문제 방지
     */
    @Query("""
        SELECT r FROM RefundRequest r
        JOIN FETCH r.payment p
        JOIN FETCH r.user u
        ORDER BY r.createdAt DESC
        """)
    List<RefundRequest> findAllWithUserAndPayment();

    /**
     * 대기 중인 환불 요청 목록 조회 (관리자용)
     * [용도] 관리자가 아직 처리하지 않은 "대기 중(PENDING)"인 건들만 따로 모아서 빠르게 승인/거절 업무를 처리할 때 사용합니다.
     * JOIN FETCH를 사용하여 N+1 문제 방지
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
     * ID로 환불 요청 조회 (User와 Payment 함께 조회)
     * [용도] 관리자가 특정 환불 건을 승인하거나 거절할 때, 해당 요청의 상세 정보(누가, 얼마를)를 정확히 불러오기 위해 사용합니다.
     * JOIN FETCH를 사용하여 N+1 문제 방지
     */
    @Query("""
        SELECT r FROM RefundRequest r
        JOIN FETCH r.payment p
        JOIN FETCH r.user u
        WHERE r.id = :id
        """)
    Optional<RefundRequest> findByIdWithUserAndPayment(@Param("id") Long id);

    /**
     * 결제 ID로 환불 요청 조회 (중복 요청 방지용)
     * [용도] 사용자가 환불 요청 버튼을 눌렀을 때, "이미 신청한 건인데 또 신청하는 건 아닌지" 중복 체크를 하기 위해 사용합니다.
     */
    Optional<RefundRequest> findByPaymentId(Long paymentId);
}
