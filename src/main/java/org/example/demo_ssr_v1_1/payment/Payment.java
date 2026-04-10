package org.example.demo_ssr_v1_1.payment;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

/**
 * Payment (결제 내역) 엔티티
 *
 * [용어 정리]
 *  · impUid      : 포트원(아임포트)에서 발급해주는 "결제 고유 번호" (포트원 서버 기준)
 *  · merchantUid : 우리 서버에서 발급하는 "주문 번호" (가맹점 기준)
 *  · status      : 결제 상태 문자열 ("paid" = 결제완료, "cancelled" = 취소됨)
 *
 * [왜 두 개의 uid 가 있나?]
 *  · merchantUid 는 "중복 결제 방지" 의 기준이 된다 (우리 DB 에 UNIQUE 제약).
 *  · impUid 는 포트원에 재검증/환불 요청 시 사용한다.
 *  · 결제 검증 흐름에서는 두 값을 모두 DB 와 일치시켜야 "같은 결제" 임을 보장할 수 있다.
 */
@Data
@NoArgsConstructor
@Table(name = "payment_tb")
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 포트원이 발급해준 결제 고유 번호 (환불·재검증에 사용) */
    @Column(unique = true, nullable = false)
    private String impUid;

    /** 우리 서버가 발급한 주문 번호 (중복 결제 방지용) */
    @Column(unique = true, nullable = false)
    private String merchantUid;

    /** 결제한 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 결제 금액 */
    @Column(nullable = false)
    private Integer amount;

    /** 결제 상태 : "paid" / "cancelled" */
    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    private Timestamp createdAt;

    @Builder
    public Payment(String impUid, String merchantUid, User user, Integer amount, String status) {
        this.impUid = impUid;
        this.merchantUid = merchantUid;
        this.user = user;
        this.amount = amount;
        this.status = status;
    }
}
