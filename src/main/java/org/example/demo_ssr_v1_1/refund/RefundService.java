package org.example.demo_ssr_v1_1.refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception500;
import org.example.demo_ssr_v1_1.payment.Payment;
import org.example.demo_ssr_v1_1.payment.PaymentRepository;
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

/**
 * Refund (환불) 도메인 Service
 *
 * [학습 포인트]
 *  1. @Transactional 의 범위 이해 : 한 메서드 안에서 "검증 → 외부 API → DB 반영" 을 한 트랜잭션으로 묶는다.
 *  2. 외부 API 연동 시 예외 처리 전략 : 외부 호출 실패를 Exception400/500 으로 감싸 트랜잭션이 롤백되게 만든다.
 *  3. 비즈니스 로직 순서 : "검증 먼저, 외부 API, 그 다음 DB 변경" 의 패턴.
 *
 * [왜 '검증 → 외부 → DB' 순서인가?]
 *  · 검증을 먼저 해야 쓸데없이 포트원에 API 호출을 안 하게 된다(비용/네트워크 절약).
 *  · 외부 API 를 먼저 성공시켜야 "돈은 돌려줬는데 우리 DB 는 그대로" 같은 사고를 피할 수 있다.
 *  · 외부 API 성공 뒤 DB 변경은 실패 확률이 낮지만, 만약 실패해도 @Transactional 롤백으로
 *    적어도 포트원 쪽 성공 이력이 남기 때문에 수동 보정이 가능하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Value("${portone.imp-key}")
    private String impKey;

    @Value("${portone.imp-secret}")
    private String impSecret;

    // =========================================================================
    // 화면 진입 검증
    // =========================================================================

    /**
     * 환불 요청 "화면" 을 띄우기 전에 필요한 검증 (컨트롤러가 아닌 서비스에 모아둔다).
     *
     * [동작 흐름]
     *  1) paymentId 로 결제 내역 조회 (User 함께 로딩)
     *  2) 본인 결제인지 확인 (아니면 403)
     *  3) 상태가 "paid" 인지 확인 (아니면 400)
     *  4) 이미 진행 중인 환불 요청이 없는지 확인 (있으면 400)
     *  5) 검증이 끝난 Payment 를 그대로 반환 (뷰에서 쓰기 좋게)
     */
    @Transactional(readOnly = true)
    public Payment 환불요청화면검증(Long paymentId, Long userId) {
        // 1. 결제 조회 (+ 작성 사용자)
        Payment payment = paymentRepository.findByIdWithUser(paymentId)
                .orElseThrow(() -> new Exception404("결제 내역을 찾을 수 없습니다"));

        // 2. 본인 결제인지 확인
        if (!payment.getUser().getId().equals(userId)) {
            throw new Exception403("본인의 결제 내역만 환불 요청할 수 있습니다");
        }

        // 3. "paid" 상태만 환불 요청 허용
        if (!"paid".equals(payment.getStatus())) {
            throw new Exception400("결제 완료된 건만 환불 요청할 수 있습니다");
        }

        // 4. 이미 진행 중인 환불 요청이 있는지
        if (refundRequestRepository.findByPaymentId(paymentId).isPresent()) {
            throw new Exception400("이미 환불 요청이 진행 중입니다. 결과 처리를 기다려주세요.");
        }

        return payment;
    }

    // =========================================================================
    // 사용자 - 환불 요청 저장
    // =========================================================================

    /**
     * 사용자의 환불 요청 등록.
     *
     * [동작 흐름]
     *  1) DTO 유효성 검사
     *  2) 위에서 정의한 화면 검증 로직 재사용 (중복 제거)
     *  3) 현재 트랜잭션 내에서 User 를 다시 조회 (영속 상태로 만들기)
     *  4) RefundRequest(status=PENDING) 저장
     */
    @Transactional
    public void 환불요청(Long userId, RefundResponse.RequestDTO requestDTO) {
        // 1. DTO 검증
        requestDTO.validate();

        // 2. 상태 검증 재사용
        Payment payment = 환불요청화면검증(requestDTO.getPaymentId(), userId);

        // 3. 사용자 재조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 4. 엔티티 생성 후 저장 (status 는 PENDING 으로 초기화됨)
        RefundRequest refundRequest = RefundRequest.builder()
                .user(user)
                .payment(payment)
                .reason(requestDTO.getReason())
                .build();

        refundRequestRepository.save(refundRequest);
        log.info("환불 요청 저장 완료: userId={}, paymentId={}", userId, payment.getId());
    }

    // =========================================================================
    // 조회
    // =========================================================================

    /** 사용자 본인의 환불 요청 목록 */
    @Transactional(readOnly = true)
    public List<RefundResponse.ListDTO> 환불요청목록조회(Long userId) {
        return refundRequestRepository.findAllByUserId(userId).stream()
                .map(RefundResponse.ListDTO::new)
                .toList();
    }

    /** 관리자용 : 전체 환불 요청 목록 (상태 무관) */
    @Transactional(readOnly = true)
    public List<RefundResponse.AdminListDTO> 관리자환불요청목록조회() {
        return refundRequestRepository.findAllWithUserAndPayment().stream()
                .map(RefundResponse.AdminListDTO::new)
                .toList();
    }

    // =========================================================================
    // 관리자 - 환불 거절
    // =========================================================================

    /**
     * 환불 거절 처리.
     *
     * [동작 흐름]
     *  1) 환불 요청 조회 (없으면 404)
     *  2) PENDING 상태가 아니면 400 (이미 처리된 요청 재처리 방지)
     *  3) 거절 사유 유효성 검사
     *  4) 상태를 REJECTED 로 전이 + 사유 기록 (더티 체킹으로 UPDATE)
     */
    @Transactional
    public void 환불거절(Long refundRequestId, String rejectReason) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new Exception404("환불 요청을 찾을 수 없습니다"));

        if (!refundRequest.isPending()) {
            throw new Exception400("대기 중인 환불 요청만 거절할 수 있습니다");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new Exception400("거절 사유를 입력해주세요");
        }

        refundRequest.reject(rejectReason);
    }

    // =========================================================================
    // 관리자 - 환불 승인 (핵심 플로우)
    // =========================================================================

    /**
     * 관리자가 환불을 최종 승인한다.
     *
     * [동작 흐름 - 순서가 곧 안전장치]
     *  1) 환불 요청 조회 (+ Payment, User 로딩)
     *  2) PENDING 상태인지 확인
     *  3) 포인트 잔액 검증 (이미 다 써버렸다면 환불 불가)
     *  4) 포트원 결제 취소 API 호출 (여기서 실패하면 예외 → 트랜잭션 롤백)
     *  5) 사용자 포인트 차감
     *  6) Payment 상태 "cancelled" 로 변경
     *  7) RefundRequest 를 APPROVED 로 전이
     *  → 이후 커밋 시 더티 체킹으로 3건(User, Payment, RefundRequest) 이 한꺼번에 UPDATE 된다.
     *
     * [주의 - 분산 트랜잭션의 한계]
     *  · 포트원 API 는 성공했는데 우리 DB 커밋에 실패하는 경우
     *    "포트원 쪽은 환불, 우리 DB 는 미처리" 의 불일치가 발생할 수 있다.
     *  · 실무에서는 보상 트랜잭션/아웃박스 패턴이 필요하지만
     *    학습 예제 수준에서는 @Transactional 롤백 + 로그로 수동 보정 가능하다고 전제한다.
     */
    @Transactional
    public void 환불승인(Long refundRequestId) {
        // 1. 조회
        RefundRequest refundRequest = refundRequestRepository.findByIdWithUserAndPayment(refundRequestId)
                .orElseThrow(() -> new Exception404("환불 요청을 찾을 수 없습니다"));

        // 2. 상태 검증
        if (!refundRequest.isPending()) {
            throw new Exception400("대기 중인 환불 요청만 승인할 수 있습니다");
        }

        Payment payment = refundRequest.getPayment();
        User user = refundRequest.getUser();
        Integer refundAmount = payment.getAmount();

        // 3. 포인트 잔액 검증 (마이너스 포인트 방지)
        if (user.getPoint() < refundAmount) {
            throw new Exception400("사용자의 포인트 잔액이 부족하여 환불할 수 없습니다. (현재: "
                    + user.getPoint() + ")");
        }

        // 4. 외부 API 호출 (실패 시 예외 → 트랜잭션 롤백)
        포트원결제취소(payment.getImpUid(), refundAmount);

        // 5. 포인트 차감
        user.deductPoint(refundAmount);

        // 6. Payment 상태 변경 (paid → cancelled)
        payment.setStatus("cancelled");

        // 7. 환불 요청 상태 전이 (PENDING → APPROVED)
        refundRequest.approve();

        log.info("환불 승인 완료: refundId={}, amount={}", refundRequestId, refundAmount);
    }

    // =========================================================================
    // 포트원 API 호출 (private helper)
    // =========================================================================

    /**
     * 포트원 결제 취소 API 호출.
     *
     * [동작 흐름]
     *  1) 토큰 발급
     *  2) 요청 헤더/바디 구성 (imp_uid, amount, reason)
     *  3) POST https://api.iamport.kr/payments/cancel 호출
     *  4) 응답 바디의 code 가 0 이면 성공, 아니면 실패 메시지로 400 예외
     *  5) 예상치 못한 예외는 500 으로 감싼다
     */
    private void 포트원결제취소(String impUid, Integer amount) {
        // 1. 토큰
        String accessToken = 포트원액세스토큰발급();

        // 2. 요청 구성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("imp_uid", impUid);
        body.put("amount", amount);
        body.put("reason", "관리자 환불 승인");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 3. 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.iamport.kr/payments/cancel",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            // 4. 응답 바디 검증
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new Exception500("포트원 응답이 비어있습니다.");
            }
            Integer code = (Integer) responseBody.get("code");
            if (code != 0) {
                String message = (String) responseBody.get("message");
                throw new Exception400("포트원 환불 실패: " + message);
            }

        } catch (Exception e) {
            log.error("포트원 결제 취소 중 오류: {}", e.getMessage());
            // 비즈니스적으로 의미 있는 400 은 그대로 재전파, 나머지는 500 으로 감싼다.
            if (e instanceof Exception400) {
                throw e;
            }
            throw new Exception500("포트원 결제 취소 연동 중 오류가 발생했습니다.");
        }
    }

    /**
     * 포트원 토큰 발급.
     *
     * [동작 흐름]
     *  1) imp_key + imp_secret 를 JSON 으로 POST
     *  2) 응답 response.access_token 을 꺼내 반환
     */
    @SuppressWarnings("unchecked")
    private String 포트원액세스토큰발급() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("imp_key", impKey);
        body.put("imp_secret", impSecret);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.iamport.kr/users/getToken",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || responseBody.get("response") == null) {
                throw new Exception500("포트원 토큰 발급 실패: 응답 없음");
            }

            Map<String, Object> responseData = (Map<String, Object>) responseBody.get("response");
            return (String) responseData.get("access_token");

        } catch (Exception e) {
            log.error("포트원 토큰 발급 중 오류: {}", e.getMessage());
            throw new Exception500("포트원 인증 토큰 발급에 실패했습니다.");
        }
    }
}
