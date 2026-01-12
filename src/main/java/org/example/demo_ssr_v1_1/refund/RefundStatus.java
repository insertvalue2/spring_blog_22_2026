package org.example.demo_ssr_v1_1.refund;

/**
 * 환불 상태 열거형
 */
public enum RefundStatus {
    PENDING,    // 대기 중
    APPROVED,   // 승인됨
    REJECTED    // 거절됨
}