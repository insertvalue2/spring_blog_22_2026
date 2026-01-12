package org.example.demo_ssr_v1_1.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;

/**
 * 결제 내역 Repository 인터페이스
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * imp_uid로 결제 내역 조회
     *
     * @param impUid 포트원 결제 고유 번호
     * @return 결제 내역 (Optional)
     */
    Optional<Payment> findByImpUid(String impUid);

    /**
     * merchant_uid로 결제 내역 조회
     *
     * @param merchantUid 가맹점 주문 번호
     * @return 결제 내역 (Optional)
     */
    Optional<Payment> findByMerchantUid(String merchantUid);

    /**
     * merchant_uid 중복 확인
     *
     * @param merchantUid 가맹점 주문 번호
     * @return 존재 여부
     */
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.merchantUid = :merchantUid")
    boolean existsByMerchantUid(@Param("merchantUid") String merchantUid);

    /**
     * 사용자별 결제 내역 조회 (최신순)
     * 
     * @param userId 사용자 ID
     * @return 결제 내역 목록
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.user.id = :userId
        ORDER BY p.createdAt DESC
        """)
    List<Payment> findAllByUserId(@Param("userId") Long userId);


    // 신규 추가
    /**
     * ID로 결제 내역 조회 (User 함께 조회)
     * JOIN FETCH를 사용하여 N+1 문제 방지 및 OSIV false 환경 대응
     *
     * @param id 결제 ID
     * @return 결제 내역 (Optional)
     */
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.user u
        WHERE p.id = :id
        """)
    Optional<Payment> findByIdWithUser(@Param("id") Long id);

}