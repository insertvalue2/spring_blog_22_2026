package org.example.demo_ssr_v1_1.refund;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

/**
 * Refund 도메인 요청 / 응답 DTO 묶음.
 *
 * [네이밍 메모]
 *  · 이 도메인은 엔티티 이름이 RefundRequest 라, 다른 도메인의 XxxRequest(요청 DTO 묶음) 컨벤션과 겹칠 수 있어서
 *    요청/응답 DTO 를 모두 RefundResponse 안에 두었다.
 *  · 예) 사용자가 보내는 RequestDTO 는 "요청" 의미지만 이 파일 안에 함께 정의한다.
 */
public class RefundResponse {

    // =========================================================================
    // 환불 요청 입력 DTO (사용자가 환불을 요청할 때 화면에서 전달)
    // =========================================================================
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

    // =========================================================================
    // 사용자 본인의 환불 요청 목록 DTO
    // =========================================================================

    /**
     * [학습 포인트 - Mustache 와 boolean 플래그]
     *  · Mustache 는 if/else 문법이 없고, {{#flag}}...{{/flag}} 섹션만 있다.
     *  · 그래서 "상태가 PENDING 이면 대기 아이콘, APPROVED 면 체크 아이콘..." 같은 표현을 하려면
     *    DTO 에 상태별 boolean 을 미리 세팅해 두는 편이 가장 단순하다.
     */
    @Data
    public static class ListDTO {
        private Long id;
        private Long paymentId;
        private Integer amount;
        private String reason;
        private String statusDisplay;   // 화면용 한글 상태 라벨
        private String rejectReason;

        // 화면 분기용 boolean 플래그
        private boolean isPending;
        private boolean isApproved;
        private boolean isRejected;

        public ListDTO(RefundRequest refundRequest) {
            this.id = refundRequest.getId();
            this.paymentId = refundRequest.getPayment().getId();
            this.amount = refundRequest.getPayment().getAmount();
            this.reason = refundRequest.getReason();
            this.rejectReason = refundRequest.getRejectReason();

            // 1. 한글 라벨 세팅
            //    JDK 14+ 의 arrow switch 를 사용 (break 불필요, 표현식이므로 실수 줄어듦)
            this.statusDisplay = switch (refundRequest.getStatus()) {
                case PENDING -> "대기중";
                case APPROVED -> "승인됨";
                case REJECTED -> "거절됨";
            };

            // 2. 화면 섹션용 boolean 플래그
            this.isPending = refundRequest.isPending();
            this.isApproved = refundRequest.isApproved();
            this.isRejected = refundRequest.isRejected();
        }
    }

    // =========================================================================
    // 관리자 환불 관리 화면용 DTO
    // =========================================================================
    @Data
    public static class AdminListDTO {
        private Long id;
        private Long paymentId;
        private String impUid;         // 포트원 결제 번호
        private String merchantUid;    // 우리 서버 주문번호
        private Integer amount;        // 결제/환불 금액
        private String userName;       // 요청자 이름
        private String reason;         // 환불 사유
        private String statusDisplay;  // 한글 상태 라벨
        private String rejectReason;   // 거절 사유
        private String requestedAt;    // 요청 시각
        private String updatedAt;      // 처리 시각
        private RefundStatus status;   // enum 그대로도 전달 (Mustache 조건 분기에 사용)

        public AdminListDTO(RefundRequest refundRequest) {
            // 1. 식별/결제 정보
            this.id = refundRequest.getId();
            this.paymentId = refundRequest.getPayment().getId();
            this.impUid = refundRequest.getPayment().getImpUid();
            this.merchantUid = refundRequest.getPayment().getMerchantUid();
            this.amount = refundRequest.getPayment().getAmount();

            // 2. 요청자 정보
            this.userName = refundRequest.getUser().getUsername();

            // 3. 사유 / 거절 사유 / 상태
            this.reason = refundRequest.getReason();
            this.rejectReason = refundRequest.getRejectReason();
            this.status = refundRequest.getStatus();
            this.statusDisplay = switch (refundRequest.getStatus()) {
                case PENDING -> "대기중";
                case APPROVED -> "승인됨";
                case REJECTED -> "거절됨";
            };

            // 4. 날짜 포맷팅
            if (refundRequest.getCreatedAt() != null) {
                this.requestedAt = MyDateUtil.timestampFormat(refundRequest.getCreatedAt());
            }
            if (refundRequest.getUpdatedAt() != null) {
                this.updatedAt = MyDateUtil.timestampFormat(refundRequest.getUpdatedAt());
            }
        }
    }
}
