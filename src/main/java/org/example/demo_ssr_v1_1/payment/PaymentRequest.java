package org.example.demo_ssr_v1_1.payment;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;

public class PaymentRequest {

    /**
     * 결제 요청 생성 DTO
     */
    @Data
    public static class PrepareDTO {
        private Integer amount;  // 충전할 포인트
        /**
         * 유효성 검사
         *
         * @throws Exception400 포인트가 0 이하일 경우
         */
        public void validate() {
            if (amount == null || amount <= 0) {
                throw new Exception400("충전할 포인트는 0보다 커야 합니다");
            }
            // 최소/최대 금액 제한 (선택사항)
            if (amount < 100) {
                throw new Exception400("최소 충전 금액은 100포인트입니다");
            }
            if (amount > 100000) {
                throw new Exception400("최대 충전 금액은 100,000포인트입니다");
            }
        }
    }

    /**
     * 결제 검증 DTO
     *
     * 프론트엔드에서 snake_case (imp_uid, merchant_uid)로 보내므로
     * @JsonNaming 어노테이션으로 자동 매핑
     */
    @Data
    //@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VerifyDTO {
        private String impUid;  // 포트원 결제 고유 번호 (JSON: imp_uid)
        private String merchantUid;  // 가맹점 주문 번호 (JSON: merchant_uid)

        /**
         * 유효성 검사
         *
         * @throws Exception400 필수 값이 없을 경우
         */
        public void validate() {
            if (impUid == null || impUid.trim().isEmpty()) {
                throw new Exception400("결제 고유 번호가 필요합니다");
            }
            if (merchantUid == null || merchantUid.trim().isEmpty()) {
                throw new Exception400("주문 번호가 필요합니다");
            }
        }
    }
}
