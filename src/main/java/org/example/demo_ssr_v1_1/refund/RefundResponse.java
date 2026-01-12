package org.example.demo_ssr_v1_1.refund;


import lombok.Data;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

/**
 * 환불 요청 응답 DTO
 */
public class RefundResponse {

    /**
     * 환불 요청 DTO (사용자 요청용)
     */
    @Data
    public static class RequestDTO {
        private Long paymentId;
        private String reason;

        public void validate() {
            if (paymentId == null) {
                throw new Exception400("결제 ID는 필수입니다");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new Exception400("환불 사유는 필수입니다");
            }
            if (reason.length() > 500) {
                throw new Exception400("환불 사유는 500자 이하여야 합니다");
            }
        }
    }

    public static class ListDTO {
        private Long id;
        private Long paymentId;
        private Integer amount;
        private String reason;
        private String statusDisplay; // 화면 표시용 텍스트 (예: "대기중")
        private String rejectReason;

        // [핵심] Mustache를 위한 상태별 Boolean 플래그
        private boolean isPending;
        private boolean isApproved;
        private boolean isRejected;

        public ListDTO(RefundRequest refundRequest) {
            this.id = refundRequest.getId();
            this.paymentId = refundRequest.getPayment().getId();
            this.amount = refundRequest.getPayment().getAmount();
            this.reason = refundRequest.getReason();
            this.rejectReason = refundRequest.getRejectReason();

            // 1. 상태 텍스트 설정
            // Switch Expression (스위치 표현식) - jdk 14 버전 부터 사용 가능
            // Arrow Switch 이라고도 부름 - break; 가 필요 없음 !
            switch (refundRequest.getStatus()) {
                case PENDING -> this.statusDisplay = "대기중";
                case APPROVED -> this.statusDisplay = "승인됨";
                case REJECTED -> this.statusDisplay = "거절됨";
            }

            // 2. [리팩토링 포인트] 화면 제어용 Boolean 값 설정
            this.isPending = (refundRequest.getStatus() == RefundStatus.PENDING);
            this.isApproved = (refundRequest.getStatus() == RefundStatus.APPROVED);
            this.isRejected = (refundRequest.getStatus() == RefundStatus.REJECTED);

        }
    }

    /**
     * 관리자용 환불 요청 목록 DTO
     */
    @Data
    public static class AdminListDTO {
        private Long id;
        private Long paymentId;
        private String impUid;            // 포트원 결제 번호
        private String merchantUid;      // 주문 번호
        private Integer amount;             // 환불 금액
        private String userName;           // 요청자 이름
        private String reason;             // 환불 사유
        private String statusDisplay;       // 상태 표시명
        private String rejectReason;       // 거절 사유
        private String requestedAt;        // 요청일시
        private String updatedAt;           // 처리일시
        private RefundStatus status;  // 상태 (Mustache 조건부 렌더링용)

        public AdminListDTO(RefundRequest refundRequest) {
            this.id = refundRequest.getId();
            this.paymentId = refundRequest.getPayment().getId();
            this.impUid = refundRequest.getPayment().getImpUid();
            this.merchantUid = refundRequest.getPayment().getMerchantUid();
            this.amount = refundRequest.getPayment().getAmount();
            this.userName = refundRequest.getUser().getUsername();
            this.reason = refundRequest.getReason();
            this.rejectReason = refundRequest.getRejectReason();
            this.status = refundRequest.getStatus();  // 상태 저장

            // 상태 표시명 변환
            // Switch Expression (스위치 표현식) - jdk 14 버전 부터 사용 가능
            // Arrow Switch 이라고도 부름 - break; 가 필요 없음 !
            switch (refundRequest.getStatus()) {
                case PENDING -> this.statusDisplay = "대기중";
                case APPROVED -> this.statusDisplay = "승인됨";
                case REJECTED -> this.statusDisplay = "거절됨";
            }

            // 날짜 포맷팅
            if (refundRequest.getCreatedAt() != null) {
                this.requestedAt = MyDateUtil.timestampFormat(refundRequest.getCreatedAt());
            }
            if (refundRequest.getUpdatedAt() != null) {
                this.updatedAt = MyDateUtil.timestampFormat(refundRequest.getUpdatedAt());
            }
        }
    }
}