package org.example.demo_ssr_v1_1.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1.refund.RefundRequest;
import org.example.demo_ssr_v1_1.refund.RefundRequestRepository;
import org.example.demo_ssr_v1_1.user.User;
import org.example.demo_ssr_v1_1.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment 도메인 Service
 *
 * [핵심 역할]
 *  1) 결제 요청 생성 (주문번호 발급)   - 프론트가 결제창을 띄우기 전에 호출
 *  2) 결제 검증 및 포인트 충전         - 결제 완료 후 서버에서 재검증
 *  3) 결제 내역 목록 조회              - 마이페이지 "결제 내역"
 *
 * [외부 API 연동 - PortOne(아임포트)]
 *  · 공식 도메인 : https://api.iamport.kr
 *  · 인증 : 토큰 발급 API → Bearer 토큰 획득 → 이후 호출에 사용
 *  · 검증 원칙 : 프론트가 보낸 "impUid/merchantUid" 를 그대로 믿지 않고,
 *               포트원 서버에 다시 물어봐서 status/amount 를 우리 DB 와 대조한다.
 *
 * [클래스 레벨 @Transactional(readOnly=true)]
 *  · 기본은 "읽기 전용" 으로 두고, 쓰기 메서드에만 개별적으로 @Transactional 을 더 붙인다.
 *  · 개별 @Transactional 은 클래스 레벨 설정을 override 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RefundRequestRepository refundRequestRepository;

    @Value("${portone.imp-key}")
    private String impKey;

    @Value("${portone.imp-secret}")
    private String impSecret;

    // =========================================================================
    // 1. 결제 요청 생성 (Prepare)
    // =========================================================================

    /**
     * 결제 요청을 위한 주문번호를 발급한다.
     *
     * [동작 흐름]
     *  1) 사용자 존재 여부 확인
     *  2) UUID + timestamp 로 고유 merchantUid 생성
     *  3) 혹시라도 DB 에 동일 값이 있으면 재생성 (거의 0% 확률)
     *  4) 프론트에 merchantUid + amount + impKey 반환
     *
     * [왜 서버에서 발급하나?]
     *  · "같은 주문번호로 두 번 결제하는 사고" 를 방지하기 위해 서버가 통제한다.
     *  · merchantUid 에 UNIQUE 제약을 걸어 중복 결제를 DB 수준에서도 막는다.
     */
    @Transactional
    public PaymentResponse.PrepareDTO 결제요청생성(Long userId, Integer amount) {
        // 1. 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new Exception404("사용자를 찾을 수 없습니다");
        }

        // 2. 주문번호 생성 (+ 중복이면 재발급)
        String merchantUid = generateMerchantUid(userId);
        while (paymentRepository.existsByMerchantUid(merchantUid)) {
            merchantUid = generateMerchantUid(userId);
        }

        // 3. 프론트가 결제창을 띄울 때 필요한 정보 반환
        return new PaymentResponse.PrepareDTO(merchantUid, amount, impKey);
    }

    // =========================================================================
    // 2. 결제 검증 및 포인트 충전 (Verify)
    // =========================================================================

    /**
     * 포트원 결제 완료 후, 결제 데이터를 서버에서 재검증하고 포인트를 지급한다.
     *
     * [동작 흐름]
     *  1) userId 로 사용자 조회
     *  2) 이미 처리한 impUid 인지 확인 (중복 지급 방지)
     *  3) 포트원 API 로 결제 상세 조회 → 상태/주문번호 검증
     *  4) 검증된 amount 만큼 user.chargePoint() 호출 (더티 체킹 UPDATE)
     *  5) Payment 엔티티 저장 (영수증 역할)
     *  6) VerifyDTO 로 프론트에 결과 반환
     *
     * [보안 원칙 - "금액은 프론트가 아니라 포트원이 말한 대로"]
     *  · 프론트는 조작될 수 있으므로, 서버는 포트원에 "진짜 얼마 결제됐어?" 를 다시 물어본다.
     *  · 3번 단계에서 얻은 amount 만 신뢰한다.
     */
    @Transactional
    public PaymentResponse.VerifyDTO 결제검증및충전(Long userId, String impUid, String merchantUid) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 2. 이미 처리된 결제인지 확인 (중복 지급 방지)
        if (paymentRepository.findByImpUid(impUid).isPresent()) {
            throw new Exception400("이미 처리된 결제입니다");
        }

        // 3. 포트원에 "실제 결제 정보" 를 다시 물어본다
        PaymentResponse.PortOnePaymentResponse.PaymentData paymentData =
                포트원결제조회(impUid, merchantUid);

        // 4. 검증된 금액으로 포인트 충전 (더티 체킹)
        Integer amount = paymentData.getAmount();
        user.chargePoint(amount);

        // 5. 영수증(Payment) 저장
        Payment payment = Payment.builder()
                .impUid(impUid)
                .merchantUid(merchantUid)
                .user(user)
                .amount(amount)
                .status("paid")
                .build();
        paymentRepository.save(payment);
        log.info("결제 및 포인트 충전 완료: userId={}, amount={}", userId, amount);

        // 6. 응답 DTO 반환
        return new PaymentResponse.VerifyDTO(amount, user.getPoint());
    }

    // =========================================================================
    // 3. 결제 내역 목록 조회
    // =========================================================================

    /**
     * 특정 사용자의 결제 내역을 "환불 가능 여부" 까지 계산해서 돌려준다.
     *
     * [환불 가능 판정 규칙]
     *  · payment.status = "paid"  AND  환불 요청 없음           → 환불 가능
     *  · payment.status = "paid"  AND  환불 요청이 REJECTED     → 다시 환불 가능
     *  · payment.status = "paid"  AND  환불 요청이 PENDING/APPROVED → 불가
     *  · payment.status = "cancelled" (이미 환불됨)              → 불가
     *
     * [성능 주의]
     *  각 결제마다 refundRequestRepository.findByPaymentId 를 호출하므로 N+1 이다.
     *  학습 단계에서는 이 구조를 먼저 이해하는 것이 중요하고,
     *  실전에서는 findByPaymentIdIn(list) 로 한 번에 가져오는 개선 과제가 있다.
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse.ListDTO> 결제내역조회(Long userId) {
        List<Payment> paymentList = paymentRepository.findAllByUserId(userId);

        return paymentList.stream()
                .map(payment -> {
                    // 1. 이 결제 건에 연결된 환불 요청 조회
                    Optional<RefundRequest> refundRequestOpt =
                            refundRequestRepository.findByPaymentId(payment.getId());

                    // 2. 환불 가능 여부 계산
                    boolean isRefundable = calculateRefundable(payment, refundRequestOpt);

                    // 3. 목록 DTO 로 변환
                    return new PaymentResponse.ListDTO(payment, isRefundable);
                })
                .toList();
    }

    // =========================================================================
    // private helpers (외부 API 통신 + 유틸)
    // =========================================================================

    /**
     * 환불 가능 여부 판정.
     *
     * @param payment      판정 대상 결제
     * @param refundRequestOpt  이 결제에 걸려있는 환불 요청(있을 수도, 없을 수도 있음)
     */
    private boolean calculateRefundable(Payment payment, Optional<RefundRequest> refundRequestOpt) {
        // 1. 결제 상태가 "paid" 가 아니면(= 이미 환불) 즉시 false
        if (!"paid".equals(payment.getStatus())) {
            return false;
        }
        // 2. 환불 요청이 아예 없으면 환불 가능
        if (refundRequestOpt.isEmpty()) {
            return true;
        }
        // 3. 환불 요청이 있지만 REJECTED 상태면 다시 요청할 수 있다
        return refundRequestOpt.get().isRejected();
    }

    /**
     * merchantUid 를 생성한다.
     *
     * 형식 : point_{userId}_{timestamp}_{uuid8}
     * 예시 : point_5_1713000000000_3f2a1b9c
     */
    private String generateMerchantUid(Long userId) {
        return "point_" + userId + "_"
                + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 포트원에 결제 상세 정보를 조회하고 데이터 무결성을 검증한다.
     *
     * [동작 흐름]
     *  1) 포트원 토큰 발급
     *  2) GET https://api.iamport.kr/payments/{impUid} 호출
     *  3) 응답 body 가 null 이면 400
     *  4) status 가 "paid" 가 아니면 400
     *  5) merchantUid 가 우리 값과 다르면 400 (위조 방지)
     *  6) 검증 통과 시 PaymentData 반환
     */
    private PaymentResponse.PortOnePaymentResponse.PaymentData 포트원결제조회(String impUid, String merchantUid) {
        // 1. 토큰 발급
        String accessToken = 포트원액세스토큰발급();

        // 2. 요청 구성
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<PaymentResponse.PortOnePaymentResponse> response = restTemplate.exchange(
                    "https://api.iamport.kr/payments/" + impUid,
                    HttpMethod.GET,
                    request,
                    PaymentResponse.PortOnePaymentResponse.class
            );

            // 3. 응답 바디 검증
            PaymentResponse.PortOnePaymentResponse.PaymentData data = response.getBody().getResponse();
            if (data == null) {
                throw new Exception400("결제 정보를 찾을 수 없습니다.");
            }

            // 4. 상태 값 검증
            if (!"paid".equals(data.getStatus())) {
                throw new Exception400("결제가 완료되지 않았습니다.");
            }

            // 5. 주문번호 일치 검증 (프론트 조작 방지의 핵심)
            if (!merchantUid.equals(data.getMerchantUid())) {
                throw new Exception400("주문 번호가 일치하지 않습니다.");
            }

            return data;

        } catch (Exception e) {
            log.error("포트원 결제 조회 실패: {}", e.getMessage());
            throw new Exception400("결제 검증 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 포트원 토큰 발급.
     *
     * [동작 흐름]
     *  1) imp_key / imp_secret 를 JSON 바디로 담는다
     *  2) POST https://api.iamport.kr/users/getToken 호출
     *  3) 응답에서 access_token 을 꺼내 반환
     */
    private String 포트원액세스토큰발급() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("imp_key", impKey);
            body.put("imp_secret", impSecret);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<PaymentResponse.PortOneTokenResponse> response = restTemplate.exchange(
                    "https://api.iamport.kr/users/getToken",
                    HttpMethod.POST,
                    request,
                    PaymentResponse.PortOneTokenResponse.class
            );

            if (response.getBody() != null && response.getBody().getResponse() != null) {
                return response.getBody().getResponse().getAccessToken();
            }
            throw new Exception400("토큰 응답이 올바르지 않습니다.");

        } catch (Exception e) {
            log.error("포트원 토큰 발급 실패: {}", e.getMessage());
            throw new Exception400("포트원 인증 실패: 관리자 설정을 확인하세요.");
        }
    }
}
