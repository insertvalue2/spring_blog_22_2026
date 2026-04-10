package org.example.demo_ssr_v1_1.user;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 사용자 관련 AJAX(JSON) API 컨트롤러
 *
 * [@RestController 란?]
 * - @Controller + @ResponseBody 의 합성.
 * - 반환값(Map, DTO, List 등) 을 JSON 으로 자동 직렬화해서 응답 본문에 내려준다.
 * - 화면(View)을 렌더링하지 않으므로 Mustache 와는 무관하다.
 *
 * [엔드포인트]
 *  · POST /api/email/send    : 이메일 인증번호 발송
 *  · POST /api/email/verify  : 이메일 인증번호 검증
 *  · POST /api/point/charge  : 포인트 충전 (학습/테스트용)
 */
@RequiredArgsConstructor
@RestController
public class UserApiController {

    private final MailService mailService;
    private final UserService userService;

    /**
     * 이메일 인증번호 발송.
     *
     * @param reqDTO  이메일이 담긴 DTO
     * @return HTTP 200 + {"message": ...}
     */
    @PostMapping("/api/email/send")
    public ResponseEntity<?> sendVerificationCode(@RequestBody UserRequest.EmailCheckDTO reqDTO) {
        // 1. DTO 검증 (형식 체크)
        reqDTO.validate();

        // 2. MailService 에 발송 위임
        mailService.인증번호발송(reqDTO.getEmail());

        // 3. 성공 응답 (JSON)
        return ResponseEntity.ok().body(Map.of("message", "인증번호가 발송되었습니다."));
    }

    /**
     * 이메일 인증번호 확인.
     *
     * [동작 흐름]
     *  1) DTO/인증번호 입력 여부 검증
     *  2) 서버에 저장해둔 값과 비교 (MailService)
     *  3) 일치하면 "email_verified_<email>" 세션 플래그를 세워 둔다.
     *     → 회원가입 시 UserController 에서 이 플래그를 확인한다(이중 검증).
     */
    @PostMapping("/api/email/verify")
    public ResponseEntity<?> verifyCode(@RequestBody UserRequest.EmailCheckDTO reqDTO, HttpSession session) {
        // 1. DTO 검증
        reqDTO.validate();
        if (reqDTO.getCode() == null || reqDTO.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "인증번호를 입력해주세요."));
        }

        // 2. MailService 를 통해 인증번호 검증
        boolean isVerified = mailService.인증번호확인(reqDTO.getEmail(), reqDTO.getCode());

        // 3. 결과에 따라 응답
        if (isVerified) {
            // 이후 /join POST 에서 "이메일 인증이 끝난 가입 요청인지" 서버 측에서 재검증할 수 있도록
            // 세션에 플래그를 저장한다. (프론트 조작에 의한 우회 방지)
            session.setAttribute("email_verified_" + reqDTO.getEmail(), true);
            return ResponseEntity.ok().body(Map.of("message", "인증되었습니다."));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "인증번호가 일치하지 않습니다."));
    }

    /**
     * 포인트 충전 API (학습/테스트용).
     *
     * [동작 흐름]
     *  1) DTO 검증
     *  2) 세션에서 로그인 사용자 꺼내기 (없으면 401)
     *  3) Service 에 충전 위임
     *  4) 세션의 sessionUser 를 최신 정보로 갱신
     *  5) 충전 결과(JSON) 반환
     *
     * ⚠ 이 엔드포인트는 "학습용 단순 충전" 이다.
     *    실제 결제 흐름은 PortOne 연동 (PaymentController 쪽) 에서 다룬다.
     */
    @PostMapping("/api/point/charge")
    public ResponseEntity<?> chargePoint(@RequestBody UserRequest.PointChargeDTO reqDTO, HttpSession session) {
        // 1. DTO 검증
        reqDTO.validate();

        // 2. 세션에서 로그인 사용자 조회
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 3. 포인트 충전 로직 위임
        User updatedUser = userService.포인트충전(sessionUser.getId(), reqDTO.getAmount());

        // 4. 세션 내 사용자 정보 갱신 (포인트가 바뀌었으므로)
        session.setAttribute("sessionUser", updatedUser);

        // 5. 응답 본문에 결과 포인트까지 함께 내려준다
        return ResponseEntity.ok().body(Map.of(
                "message", "포인트가 충전되었습니다.",
                "amount", reqDTO.getAmount(),
                "currentPoint", updatedUser.getPoint()
        ));
    }
}
