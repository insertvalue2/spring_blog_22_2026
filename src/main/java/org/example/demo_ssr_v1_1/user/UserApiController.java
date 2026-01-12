package org.example.demo_ssr_v1_1.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 사용자 API 컨트롤러
 * 
 * AJAX 요청을 처리하는 컨트롤러입니다.
 * 
 * @RestController: 
 * - @Controller + @ResponseBody
 * - 반환값을 JSON으로 자동 변환
 * - 화면(View)을 반환하지 않고 데이터만 반환
 * 
 * 핵심 개념:
 * 1. AJAX 통신: 페이지 새로고침 없이 서버와 데이터 주고받기
 * 2. JSON 형식: JavaScript와 서버 간 데이터 교환 형식
 * 3. ResponseEntity: HTTP 상태 코드와 본문을 함께 반환
 * 
 * API 엔드포인트:
 * - POST /api/email/send: 인증번호 발송
 * - POST /api/email/verify: 인증번호 확인
 * - POST /api/point/charge: 포인트 충전 (테스트용)
 */
@RequiredArgsConstructor
@RestController // 데이터만 리턴 (JSON)
public class UserApiController {

    private final MailService mailService;
    private final UserService userService;

    /**
     * 인증번호 발송 API
     * @param reqDTO 이메일 주소가 담긴 DTO
     * @return 성공 메시지 (JSON)
     */
    @PostMapping("/api/email/send")
    public ResponseEntity<?> sendVerificationCode(@RequestBody UserRequest.EmailCheckDTO reqDTO) {
        // 1. 유효성 검사
        reqDTO.validate();

        // 2. 메일 서비스에 인증번호 발송 요청
        mailService.인증번호발송(reqDTO.getEmail());

        // 3. 성공 응답 반환 (JSON 형식)
        // Map.of(): 간단한 JSON 객체 생성 (Java 9+)
        // - "message": "인증번호가 발송되었습니다."
        return ResponseEntity.ok().body(Map.of("message", "인증번호가 발송되었습니다."));
    }

    /**
     * 인증번호 확인 API
     * 
     * 처리 과정:
     * 1. DTO 유효성 검사
     * 2. 메일 서비스에 인증번호 확인 요청
     * 3. 인증 성공 시 세션에 인증 완료 플래그 저장
     * 4. 성공/실패 응답 반환
     * 
     * @param reqDTO 이메일 주소와 인증번호가 담긴 DTO
     * @param session 세션 (인증 완료 플래그 저장용)
     * @return 인증 결과 메시지 (JSON)
     */
    @PostMapping("/api/email/verify")
    public ResponseEntity<?> verifyCode(@RequestBody UserRequest.EmailCheckDTO reqDTO, jakarta.servlet.http.HttpSession session) {
        // 1. 유효성 검사
        // - 이메일이 비어있지 않은지 확인
        // - 이메일 형식이 올바른지 확인
        reqDTO.validate();
        
        // 인증번호 입력 확인
        if (reqDTO.getCode() == null || reqDTO.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증번호를 입력해주세요."));
        }

        // 2. 메일 서비스에 인증번호 확인 요청
        // - 세션에서 저장된 인증번호 가져오기
        // - 사용자가 입력한 인증번호와 비교
        // - 일치하면 세션에서 삭제 (일회용)
        boolean isVerified = mailService.인증번호확인(reqDTO.getEmail(), reqDTO.getCode());

        // 3. 결과에 따른 응답 반환
        if (isVerified) {
            // 인증 성공: 세션에 인증 완료 플래그 저장
            // - 목적: 회원가입 시 서버에서도 이메일 인증 여부를 확인하기 위함
            // - Key 이름: "email_verified_" + 이메일 (예: "email_verified_test@gmail.com")
            session.setAttribute("email_verified_" + reqDTO.getEmail(), true);
            // HTTP 200 OK
            return ResponseEntity.ok().body(Map.of("message", "인증되었습니다."));
        } else {
            // 인증 실패: HTTP 400 Bad Request
            return ResponseEntity.badRequest().body(Map.of("message", "인증번호가 일치하지 않습니다."));
        }
    }

    /**
     * 포인트 충전 API (테스트용)
     * 
     * 처리 과정:
     * 1. DTO 유효성 검사
     * 2. 세션에서 사용자 정보 추출
     * 3. 포인트 충전 처리
     * 4. 성공 응답 반환 (충전 후 포인트 포함)
     * 
     * ⚠️ 주의: 현재는 테스트용으로 세션 기반 인증을 사용합니다.
     * 추후 PG 연동 시 실제 결제 프로세스로 대체됩니다.
     * 
     * @param reqDTO 포인트 충전 DTO
     * @param session 세션 (로그인한 사용자 정보)
     * @return 충전 결과 (현재 포인트 포함)
     */
    @PostMapping("/api/point/charge")
    public ResponseEntity<?> chargePoint(@RequestBody UserRequest.PointChargeDTO reqDTO, jakarta.servlet.http.HttpSession session) {
        // 1. 유효성 검사
        reqDTO.validate();

        // 2. 세션에서 사용자 정보 추출
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. 포인트 충전 처리
        User updatedUser = userService.포인트충전(sessionUser.getId(), reqDTO.getAmount());

        // 4. 세션에 업데이트된 사용자 정보 저장
        session.setAttribute("sessionUser", updatedUser);

        // 5. 성공 응답 반환 (충전 후 포인트 포함)
        return ResponseEntity.ok().body(Map.of(
                "message", "포인트가 충전되었습니다.",
                "amount", reqDTO.getAmount(),
                "currentPoint", updatedUser.getPoint()
        ));
    }
}
