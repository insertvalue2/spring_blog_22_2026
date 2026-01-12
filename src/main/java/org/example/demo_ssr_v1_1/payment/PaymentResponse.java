package org.example.demo_ssr_v1_1.payment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

public class PaymentResponse {

    @Data
    public static class PrepareDTO {
        private String merchantUid;
        private Integer amount;
        private String impKey;

        public PrepareDTO(String merchantUid, Integer amount, String impKey) {
            this.merchantUid = merchantUid;
            this.amount = amount;
            this.impKey = impKey;
        }
    }

    // 화면(Alert창)에 띄워줄 최소한의 데이터
    @Data
    public static class VerifyDTO {
        private Integer amount;        // 충전 금액
        private Integer currentPoint;  // 현재 잔액

        public VerifyDTO(Integer amount, Integer currentPoint) {
            this.amount = amount;
            this.currentPoint = currentPoint;
        }
    }

    // 포트원 토큰 응답용 내부 DTO
    @Data
    @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PortOneTokenResponse {
        private int code;
        private String message;
        private ResponseData response;

        @Data
        @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class ResponseData {
            private String accessToken;
            private int now;
            private int expiredAt;
        }
    }

    // 포트원 결제 조회 응답용 내부 DTO
    @Data
    @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PortOnePaymentResponse {
        private int code;
        private String message;
        private PaymentData response;

        @Data
        @JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class PaymentData {
            private String impUid;
            private String merchantUid;
            private Integer amount;
            private String status;
            private Long paidAt;
        }
    }

    /**
     * 결제 내역 목록 DTO
     */
    @Data
    public static class ListDTO {
        private Long id;
        private String impUid;          // 포트원 결제 번호
        private String merchantUid;    // 주문 번호
        private Integer amount;         // 결제 금액
        private String status;          // 결제 상태 (paid, cancelled)
        private String statusDisplay;   // 상태 표시명
        private String paidAt;         // 결제일시
        // 추후 추가
        private Boolean isRefundable;  // 환불 가능 여부 (결제완료 상태일 때만 true)

        /**
         * 환불 요청 화면에서 사용
         */
        public ListDTO(Payment payment) {
            this(payment, "paid".equals(payment.getStatus()));
        }

        public ListDTO(Payment payment, Boolean isRefundable) {
            this.id = payment.getId();
            this.impUid = payment.getImpUid();
            this.merchantUid = payment.getMerchantUid();
            this.amount = payment.getAmount();
            this.status = payment.getStatus();
            this.isRefundable = isRefundable != null ? isRefundable : false;

            // 상태 표시명 변환
            if ("paid".equals(payment.getStatus())) {
                this.statusDisplay = "결제완료";
            } else if ("cancelled".equals(payment.getStatus())) {
                this.statusDisplay = "환불완료";
            } else {
                this.statusDisplay = payment.getStatus();
            }

            // 날짜 포맷팅
            if (payment.getCreatedAt() != null) {
                this.paidAt = MyDateUtil.timestampFormat(payment.getCreatedAt());
            }
        }
    }
}