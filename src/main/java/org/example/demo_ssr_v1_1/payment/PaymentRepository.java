package org.example.demo_ssr_v1_1.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Payment 엔티티 Repository
 *
 * [핵심 개념]
 *  · impUid / merchantUid 둘 다 DB UNIQUE 라서 단건 조회가 기본.
 *  · 결제 상세(환불 화면 등)에서는 User 를 함께 가져와야 하므로 JOIN FETCH 쿼리를 따로 제공한다.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 포트원 결제 고유 번호(impUid)로 단건 조회.
     */
    Optional<Payment> findByImpUid(String impUid);

    /**
     * 가맹점 주문 번호(merchantUid)로 단건 조회.
     */
    Optional<Payment> findByMerchantUid(String merchantUid);

    /**
     * merchantUid 중복 여부 확인.
     *
     * [왜 따로 만들어두나?]
     *  · 신규 결제 검증 시 "같은 주문번호로 이미 저장된 적 있는지" 만 빠르게 확인하면 되므로
     *    엔티티 조회 대신 COUNT 쿼리로 한 번에 판단한다.
     */
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.merchantUid = :merchantUid")
    boolean existsByMerchantUid(@Param("merchantUid") String merchantUid);

    /**
     * 특정 사용자의 결제 내역 (최신순).
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.user.id = :userId
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findAllByUserId(@Param("userId") Long userId);

    /**
     * 결제 id 로 단건 조회 + User 함께 로딩 (JOIN FETCH).
     *
     * [OSIV false 환경 고려]
     *  · 환불 화면 등 Service 밖에서 User 정보를 꺼내야 할 때
     *    Lazy 상태면 예외가 나므로 미리 함께 로딩한다.
     */
    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.user u
            WHERE p.id = :id
            """)
    Optional<Payment> findByIdWithUser(@Param("id") Long id);
}
