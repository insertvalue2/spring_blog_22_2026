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
 * 결제(Payment) API 컨트롤러
 *
 * [엔드포인트]
 *  · POST /api/payment/prepare : 결제 요청 생성 → merchantUid 발급
 *  · POST /api/payment/verify  : 결제 검증 + 포인트 충전
 *
 * [프론트 플로우와의 관계]
 *  1) 프론트 : "포인트 충전" 버튼 클릭
 *  2) 프론트 : /api/payment/prepare 호출 → merchantUid, amount, impKey 받음
 *  3) 프론트 : 포트원 JS SDK 로 결제창 띄움 (받은 merchantUid 사용)
 *  4) 사용자 : 결제 완료
 *  5) 프론트 : 포트원에서 돌려준 impUid + merchantUid 를 /api/payment/verify 로 전송
 *  6) 서버  : 포트원 API 로 다시 검증 후 포인트 지급 (이 컨트롤러의 verifyPayment)
 */
@RequiredArgsConstructor
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 요청 생성.
     *
     * [동작 흐름]
     *  1) DTO 검증
     *  2) 세션에서 로그인 사용자 꺼내기 (없으면 401)
     *  3) Service 에 merchantUid 발급 위임
     *  4) merchantUid / amount / impKey 를 JSON 으로 응답
     */
    @PostMapping("/api/payment/prepare")
    public ResponseEntity<?> preparePayment(@RequestBody PaymentRequest.PrepareDTO reqDTO,
                                            HttpSession session) {
        // 1. DTO 검증
        reqDTO.validate();

        // 2. 로그인 여부 확인
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. Service 에 주문번호 발급 위임
        PaymentResponse.PrepareDTO prepareDTO = paymentService.결제요청생성(
                sessionUser.getId(),
                reqDTO.getAmount()
        );

        // 4. 프론트가 포트원 결제창을 띄우는 데 필요한 값들을 반환
        //    (키 이름은 프론트(JS)와 합의된 snake_case 로 내려준다)
        return ResponseEntity.ok().body(Map.of(
                "merchant_uid", prepareDTO.getMerchantUid(),
                "amount", prepareDTO.getAmount(),
                "imp_key", prepareDTO.getImpKey()
        ));
    }

    /**
     * 결제 검증 + 포인트 충전.
     *
     * [동작 흐름]
     *  1) DTO 검증
     *  2) 세션 사용자 확인
     *  3) Service 에 검증 + 충전 위임 (포트원 재조회 포함)
     *  4) 세션의 sessionUser.point 값을 최신으로 갱신 (뷰에서 즉시 반영되도록)
     *  5) VerifyDTO 그대로 응답
     */
    @PostMapping("/api/payment/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentRequest.VerifyDTO reqDTO,
                                           HttpSession session) {
        // 1. DTO 검증
        reqDTO.validate();

        // 2. 로그인 여부 확인
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. Service 호출 : 포트원 재검증 + Payment 저장 + 포인트 충전
        PaymentResponse.VerifyDTO verifyDTO = paymentService.결제검증및충전(
                sessionUser.getId(),
                reqDTO.getImpUid(),
                reqDTO.getMerchantUid()
        );

        // 4. 세션 사용자 포인트 즉시 갱신 (다음 페이지 이동 시 올바른 잔액이 보이도록)
        sessionUser.setPoint(verifyDTO.getCurrentPoint());
        session.setAttribute("sessionUser", sessionUser);

        // 5. 프론트가 바로 화면에 띄울 수 있도록 결과만 내려준다
        return ResponseEntity.ok().body(verifyDTO);
    }
}
