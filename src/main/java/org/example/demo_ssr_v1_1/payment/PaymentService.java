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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 결제 서비스
 *
 * 포트원(PortOne) API와 연동하여 결제 프로세스를 처리합니다.
 * 1. 결제 요청 생성 (주문번호 발급)
 * 2. 결제 검증 및 포인트 충전 (핵심 비즈니스 로직)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RefundRequestRepository refundRequestRepository; // 추가

    @Value("${portone.imp-key}")
    private String impKey;

    @Value("${portone.imp-secret}")
    private String impSecret;

    /**
     * 1. 결제 요청 생성 (Prepare)
     *
     * 프론트엔드가 결제창을 띄우기 전에, 서버로부터 고유한 '주문번호(merchant_uid)'를 발급받습니다.
     * [목적]
     * - 중복 결제 방지 (유니크한 주문번호 보장)
     * - 금액 위변조 방지 (서버에 미리 금액을 기록해둘 수 있음 - 현재는 생략)
     *
     * @param userId 요청한 사용자 ID
     * @param amount 충전하려는 금액
     * @return 생성된 주문번호와 결제 식별코드
     */
    @Transactional
    public PaymentResponse.PrepareDTO 결제요청생성(Long userId, Integer amount) {
        // 1. 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new Exception404("사용자를 찾을 수 없습니다");
        }

        // 2. 주문번호 생성 (UUID 사용, 중복 시 재생성)
        String merchantUid = generateMerchantUid(userId);
        while (paymentRepository.existsByMerchantUid(merchantUid)) {
            merchantUid = generateMerchantUid(userId);
        }

        // 3. DTO 반환
        return new PaymentResponse.PrepareDTO(merchantUid, amount, impKey);
    }

    /**
     * 2. 결제 검증 및 포인트 충전 (Verify)
     *
     * 포트원 결제 완료 후, 프론트엔드가 보낸 정보가 조작되지 않았는지 검증하고
     * 실제 포인트를 지급합니다.
     *
     * @param userId 사용자 ID
     * @param impUid 포트원 결제 고유 번호
     * @param merchantUid 가맹점 주문 번호
     * @return 최종 충전된 금액과 현재 포인트
     */
    @Transactional
    public PaymentResponse.VerifyDTO 결제검증및충전(Long userId, String impUid, String merchantUid) {
        // [1] 사전 검증 (DB)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 이미 처리된 결제인지 확인 (중복 지급 방지)
        if (paymentRepository.findByImpUid(impUid).isPresent()) {
            throw new Exception400("이미 처리된 결제입니다");
        }

        // [2] 외부 통신 (포트원 서버 조회)
        // 복잡한 통신 로직은 아래 private 메서드로 분리하여 가독성 확보
        PaymentResponse.PortOnePaymentResponse.PaymentData paymentData = 포트원결제조회(impUid, merchantUid);

        // [3] 비즈니스 로직 (포인트 충전 및 저장)
        Integer amount = paymentData.getAmount();

        // 3-1. 포인트 충전 (Dirty Checking으로 자동 업데이트)
        user.chargePoint(amount);

        // 3-2. 결제 내역 저장 (영수증 발급)
        Payment payment = Payment.builder()
                .impUid(impUid)
                .merchantUid(merchantUid)
                .user(user)
                .amount(amount)
                .status("paid")
                .build();

        paymentRepository.save(payment);
        log.info("결제 및 포인트 충전 완료: userId={}, amount={}", userId, amount);

        // [4] 결과 반환 (필요한 데이터만)
        return new PaymentResponse.VerifyDTO(amount, user.getPoint());
    }

    // =================================================================================
    //  Private Helper Methods (외부 API 통신 및 유틸리티)
    // =================================================================================

    /**
     * 주문번호 생성 유틸리티
     * 형식: point_{userId}_{timestamp}_{uuid}
     */
    private String generateMerchantUid(Long userId) {
        return "point_" + userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 포트원 API를 통해 결제 상세 정보를 조회하고 검증합니다.
     */
    private PaymentResponse.PortOnePaymentResponse.PaymentData 포트원결제조회(String impUid, String merchantUid) {
        // 1. 액세스 토큰 발급
        String accessToken = 포트원액세스토큰발급();

        // 2. 결제 정보 조회 요청 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // "Bearer " + token
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<PaymentResponse.PortOnePaymentResponse> response = restTemplate.exchange(
                    "https://api.iamport.kr/payments/" + impUid,
                    HttpMethod.GET,
                    request,
                    PaymentResponse.PortOnePaymentResponse.class
            );

            // 3. 응답 데이터 추출
            PaymentResponse.PortOnePaymentResponse.PaymentData data = response.getBody().getResponse();

            if (data == null) throw new Exception400("결제 정보를 찾을 수 없습니다.");

            // 4. 데이터 무결성 검증
            if (!"paid".equals(data.getStatus())) throw new Exception400("결제가 완료되지 않았습니다.");
            if (!merchantUid.equals(data.getMerchantUid())) throw new Exception400("주문 번호가 일치하지 않습니다.");

            return data;

        } catch (Exception e) {
            log.error("포트원 결제 조회 실패: {}", e.getMessage());
            throw new Exception400("결제 검증 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 결제 내역 목록 조회
     *
     * 환불 요청 상태를 확인하여 isRefundable을 결정합니다.
     * - 결제 상태가 "paid"이고 환불 요청이 없거나 거절된 경우: 환불 가능
     * - 결제 상태가 "paid"이고 환불 요청이 대기 중이거나 승인된 경우: 환불 불가
     * - 결제 상태가 "cancelled"인 경우: 환불 불가
     *
     * @param userId 사용자 ID
     * @return 결제 내역 목록
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse.ListDTO> 결제내역조회(Long userId) {

        List<Payment> paymentList = paymentRepository.findAllByUserId(userId);

        // 각 결제에 대한 환불 요청 상태를 확인하여 DTO 생성
        return paymentList.stream()
                .map(payment -> {
                    // 환불 요청 조회 --> where payment_id in (1, 2, 3...) inquery 로 변경해서 처리하는게 성능상 더 좋음 !
                    Optional<RefundRequest> refundRequestOpt = refundRequestRepository.findByPaymentId(payment.getId());

                    // 환불 요청이 있는 경우 상태 확인
                    boolean hasRefundRequest = refundRequestOpt.isPresent();
                    boolean isRefundable = false;

                    if ("paid".equals(payment.getStatus())) {
                        // 결제 완료 상태인 경우
                        if (!hasRefundRequest) {
                            // 환불 요청이 없으면 환불 가능
                            isRefundable = true;
                        } else {
                            // 환불 요청이 있는 경우, 거절된 경우에만 다시 환불 요청 가능
                            RefundRequest refundRequest = refundRequestOpt.get();
                            if (refundRequest.isRejected()) {
                                // 거절된 경우 다시 환불 요청 가능
                                isRefundable = true;
                            } else {
                                // 대기 중이거나 승인된 경우 환불 불가
                                isRefundable = false;
                            }
                        }
                    } else {
                        // 이미 환불된 결제는 환불 불가
                        isRefundable = false;
                    }

                    return new PaymentResponse.ListDTO(payment, isRefundable);
                })
                .collect(Collectors.toList());
    }

    /**
     * 포트원 API 인증 토큰 발급
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