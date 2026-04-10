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
 * RefundRequest (환불 요청) 엔티티
 *
 * [상태 흐름]
 *   PENDING  → 사용자가 요청한 초기 상태
 *   APPROVED → 관리자가 승인 (PortOne 환불 API 성공 후)
 *   REJECTED → 관리자가 거절 (사유 기록)
 *
 * [Payment 와의 관계 설계]
 *  · "결제 1건에 환불 요청 1건" 을 보장하기 위해 @ManyToOne + unique 조합을 사용.
 *  · @OneToOne 도 가능하지만, 추후 "부분 환불(여러 번)" 확장을 열어두기 위해 @ManyToOne 으로 두었다.
 */
@Data
@NoArgsConstructor
@Table(name = "refund_request_tb")
@Entity
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 환불 요청자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 환불 대상 결제 (UNIQUE 제약으로 중복 요청 방지) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    /** 환불 사유 (사용자가 직접 입력) */
    @Column(length = 500)
    private String reason;

    /** 환불 상태 (enum → DB 에는 STRING 으로 저장) */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RefundStatus status = RefundStatus.PENDING;

    /** 거절 시 관리자 메모 (승인 시에는 null) */
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
        this.status = RefundStatus.PENDING; // 초기 상태는 항상 PENDING
    }

    // =========================================================================
    // 상태 전이 메서드 (도메인 로직)
    // =========================================================================

    /** PENDING → APPROVED 로 상태 전이 */
    public void approve() {
        this.status = RefundStatus.APPROVED;
    }

    /** PENDING → REJECTED 로 상태 전이 (사유 함께 기록) */
    public void reject(String rejectReason) {
        this.status = RefundStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    // =========================================================================
    // 상태 확인 헬퍼
    // =========================================================================

    public boolean isPending() {
        return this.status == RefundStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == RefundStatus.APPROVED;
    }

    public boolean isRejected() {
        return this.status == RefundStatus.REJECTED;
    }
}
