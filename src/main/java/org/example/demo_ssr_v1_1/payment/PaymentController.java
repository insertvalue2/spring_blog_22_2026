package org.example.demo_ssr_v1_1.payment;


import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 결제 컨트롤러
 *
 * 포트원 결제 연동을 처리하는 API 엔드포인트입니다.
 * - POST /api/payment/prepare: 결제 요청 생성 (merchant_uid 발급)
 * - POST /api/payment/verify: 결제 검증 및 포인트 충전
 */
@RequiredArgsConstructor
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청 생성 API
     *
     * merchant_uid를 생성하여 프론트엔드에 전달합니다.
     * 프론트엔드에서 이 merchant_uid를 사용하여 포트원 결제창을 호출합니다.
     *
     * @param reqDTO 결제 요청 DTO (충전할 포인트)
     * @param session 세션 (로그인한 사용자 정보)
     * @return 결제 요청 정보 (merchant_uid, amount, impKey)
     */
    @PostMapping("/api/payment/prepare")
    public ResponseEntity<?> preparePayment(
            @RequestBody PaymentRequest.PrepareDTO reqDTO,
            HttpSession session) {
        // 1. 유효성 검사
        reqDTO.validate();

        // 2. 세션에서 사용자 정보 추출
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. 결제 요청 생성 (merchant_uid 발급)
        PaymentResponse.PrepareDTO prepareDTO = paymentService.결제요청생성(
                sessionUser.getId(),
                reqDTO.getAmount()
        );

        // 4. 성공 응답 반환
        return ResponseEntity.ok().body(Map.of(
                "merchant_uid", prepareDTO.getMerchantUid(),
                "amount", prepareDTO.getAmount(),
                "imp_key", prepareDTO.getImpKey()
        ));
    }



    /**
     * 결제 검증 및 포인트 충전 API
     *
     * 포트원 API를 호출하여 결제 정보를 검증하고,
     * 검증 성공 시 포인트를 충전합니다.
     *
     * @param reqDTO 결제 검증 DTO (imp_uid, merchant_uid)
     * @param session 세션 (로그인한 사용자 정보)
     * @return 결제 검증 결과 (충전 후 포인트 포함)
     */
    @PostMapping("/api/payment/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestBody PaymentRequest.VerifyDTO reqDTO,
            HttpSession session) {
        // 1. 유효성 검사
        reqDTO.validate();

        // 2. 세션에서 사용자 정보 추출
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. 서비스 호출 (검증 + 충전 완료)
        PaymentResponse.VerifyDTO verifyDTO = paymentService.결제검증및충전(
                sessionUser.getId(),
                reqDTO.getImpUid(),
                reqDTO.getMerchantUid()
        );

        // 4. 세션에  사용자 포인트 정보 즉시 업데이트 (포인트 충전 반영)
        sessionUser.setPoint(verifyDTO.getCurrentPoint());
        session.setAttribute("sessionUser", sessionUser);

        // 5. 성공 응답 (필요한 것만!)
        return ResponseEntity.ok().body(verifyDTO);
    }


}
