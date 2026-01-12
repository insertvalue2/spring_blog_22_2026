package org.example.demo_ssr_v1_1.refund;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1.payment.Payment;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

/**
 * 환불 요청 엔티티
 *
 * 사용자가 환불을 요청하고, 관리자가 승인/거절하는 과정을 관리합니다.
 */
@Data
@NoArgsConstructor
@Table(name = "refund_request_tb")
@Entity
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 환불 요청한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // [확장성을 고려한 설계]
    // 현재는 '전액 환불' 정책이라 결제(1) : 환불(1) 관계.
    // 하지만 추후 '부분 환불(1:N)' 기능 도입 가능성을 열어두기 위해
    // @OneToOne 대신 @ManyToOne에 unique 제약조건을 걸어 1:1을 구현.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    // 환불 사유
    @Column(length = 500)
    private String reason;

    // 환불 상태 (PENDING: 대기, APPROVED: 승인, REJECTED: 거절)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RefundStatus status = RefundStatus.PENDING;

    // 관리자 거절 사유 (거절 시에만 사용)
    @Column(length = 500)
    private String rejectReason;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @Builder
    public RefundRequest(User user, Payment payment, String reason) {
        this.user = user;
        this.payment = payment;
        this.reason = reason;
        this.status = RefundStatus.PENDING;
    }


    /**
     * 환불 승인 처리
     */
    public void approve() {
        this.status = RefundStatus.APPROVED;
    }

    /**
     * 환불 거절 처리
     */
    public void reject(String rejectReason) {
        this.status = RefundStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    /**
     * 대기 중인 상태인지 확인
     */
    public boolean isPending() {
        return this.status == RefundStatus.PENDING;
    }

    /**
     * 승인된 상태인지 확인
     */
    public boolean isApproved() {
        return this.status == RefundStatus.APPROVED;
    }

    /**
     * 거절된 상태인지 확인
     */
    public boolean isRejected() {
        return this.status == RefundStatus.REJECTED;
    }
}
