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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 환불 서비스
 *
 * 학생들을 위한 핵심 포인트:
 * 1. @Transactional의 역할과 범위 이해하기
 * 2. 외부 API(포트원) 연동 시 예외 처리 전략
 * 3. 비즈니스 로직의 순서 (검증 -> 외부 요청 -> DB 반영)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    // 포트원 API 설정 (application.yml)
    @Value("${portone.imp-key}")
    private String impKey;

    @Value("${portone.imp-secret}")
    private String impSecret;


    /**
     * 0단계: 환불 요청 화면 진입 시 검증 (Controller 로직 이관)
     *
     * 컨트롤러에 있던 복잡한 검증 로직을 서비스로 가져왔습니다.
     * 컨트롤러는 "요청을 받고 응답을 주는" 역할에 집중해야 합니다.
     */
    @Transactional(readOnly = true)
    public Payment 환불요청화면검증(Long paymentId, Long userId) {
        // 1. 결제 내역 조회 (User 정보 함께 조회) // paymentRepository - findByIdWithUser 만들어야 함
        Payment payment = paymentRepository.findByIdWithUser(paymentId)
                .orElseThrow(() -> new Exception404("결제 내역을 찾을 수 없습니다"));

        // 2. 본인 확인
        if (!payment.getUser().getId().equals(userId)) {
            throw new Exception403("본인의 결제 내역만 환불 요청할 수 있습니다");
        }

        // 3. 결제 완료 상태인지 확인 (paid 상태만 환불 가능)
        if (!"paid".equals(payment.getStatus())) {
            throw new Exception400("결제 완료된 건만 환불 요청할 수 있습니다");
        }

        // 4. 이미 환불 요청이 진행 중인지 확인
        if (refundRequestRepository.findByPaymentId(paymentId).isPresent()) {
            throw new Exception400("이미 환불 요청이 진행 중입니다. 결과 처리를 기다려주세요.");
        }

        return payment;
    }


    /**
     * 1단계: 사용자가 환불 요청하기
     */
    @Transactional
    public void 환불요청(Long userId, RefundResponse.RequestDTO requestDTO) {
        // 1. 유효성 검사
        requestDTO.validate();

        // 2. 화면 검증 로직 재사용 (중복 코드 제거)
        Payment payment = 환불요청화면검증(requestDTO.getPaymentId(), userId);

        // 3. 사용자 조회 (영속성 컨텍스트 로딩)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 4. 환불 요청 저장
        RefundRequest refundRequest = RefundRequest.builder()
                .user(user)
                .payment(payment)
                .reason(requestDTO.getReason())
                .build();

        refundRequestRepository.save(refundRequest);
        log.info("환불 요청 저장 완료: userId={}, paymentId={}", userId, payment.getId());
    }

    /**
     * 사용자의 환불 요청 목록 조회 (세션 로그인 본인)
     */
    @Transactional(readOnly = true)
    public List<RefundResponse.ListDTO> 환불요청목록조회(Long userId) {
        List<RefundRequest> refundList = refundRequestRepository.findAllByUserId(userId);
        return refundList.stream()
                .map(RefundResponse.ListDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 3단계: 관리자가 환불 요청 목록 조회 (전체)
     */
    @Transactional(readOnly = true)
    public List<RefundResponse.AdminListDTO> 관리자환불요청목록조회() {
        List<RefundRequest> refundList = refundRequestRepository.findAllWithUserAndPayment();
        return refundList.stream()
                .map(RefundResponse.AdminListDTO::new)
                .toList();
    }


    /**
     * 4단계: 관리자가 환불 거절하기
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
        // 더티 체킹으로 자동 저장
    }
    /**
     * 관리자가 환불 승인하기 (핵심 기능)
     *
     * [트랜잭션 처리 순서]
     * 1. 데이터 검증 (상태, 잔액 등)
     * 2. 외부 API 호출 (포트원 결제 취소)
     * 3. DB 데이터 업데이트 (포인트 차감, 상태 변경)
     *
     * 주의: 외부 API 호출이 성공하고 DB 업데이트가 실패하면 데이터 불일치가 발생할 수 있습니다.
     * 실무에서는 이를 방지하기 위해 '분산 트랜잭션'이나 '보상 트랜잭션' 패턴을 사용하지만,
     * 여기서는 @Transactional을 통해 예외 발생 시 롤백되도록 처리합니다.
     */

    @Transactional
    public void 환불승인(Long refundRequestId) {
        // 1. 환불 요청 조회
        RefundRequest refundRequest = refundRequestRepository.findByIdWithUserAndPayment(refundRequestId)
                .orElseThrow(() -> new Exception404("환불 요청을 찾을 수 없습니다"));

        // 2. 상태 검증
        if (!refundRequest.isPending()) {
            throw new Exception400("대기 중인 환불 요청만 승인할 수 있습니다");
        }

        Payment payment = refundRequest.getPayment();
        User user = refundRequest.getUser();
        Integer refundAmount = payment.getAmount();

        // 3. 포인트 잔액 검증 (이미 포인트를 다 썼다면 환불 불가)
        if (user.getPoint() < refundAmount) {
            throw new Exception400("사용자의 포인트 잔액이 부족하여 환불할 수 없습니다. (현재: " + user.getPoint() + ")");
        }

        // 4. 포트원 결제 취소 API 호출
        // 이 단계에서 실패하면 예외가 발생하고 트랜잭션이 롤백되어 아무 일도 없던 것처럼 됩니다.
        포트원결제취소(payment.getImpUid(), refundAmount);

        // 5. DB 업데이트 (포인트 차감 및 상태 변경)
        user.deductPoint(refundAmount); // 포인트 차감
        payment.setStatus("cancelled"); // 결제 상태 변경
        refundRequest.approve();        // 환불 요청 상태 변경

        // 더티 체킹(Dirty Checking)에 의해 트랜잭션 종료 시 자동 update 쿼리가 나갑니다.
        log.info("환불 승인 완료: refundId={}, amount={}", refundRequestId, refundAmount);
    }

    /**
     * 포트원 결제 취소 요청
     */
    private void 포트원결제취소(String impUid, Integer amount) {
        // 1. 액세스 토큰 발급
        String accessToken = 포트원액세스토큰발급();

        // 2. 요청 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        // 3. 요청 바디 설정
        Map<String, Object> body = new HashMap<>();
        body.put("imp_uid", impUid);
        body.put("amount", amount);
        body.put("reason", "관리자 환불 승인");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 4. API 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.iamport.kr/payments/cancel",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            // 5. 응답 처리
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
            // 여기서 예외를 던져야 트랜잭션이 롤백됩니다.
            if (e instanceof Exception400) throw e;
            throw new Exception500("포트원 결제 취소 연동 중 오류가 발생했습니다.");
        }
    }

    /**
     * 포트원 액세스 토큰 발급 요청
     */
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