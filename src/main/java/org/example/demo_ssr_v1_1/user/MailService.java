package org.example.demo_ssr_v1_1.user;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.utils.MailUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증(회원가입용) 서비스
 *
 * [기능 요약]
 *  1) 인증번호를 생성하고 사용자에게 메일로 발송한다.
 *  2) 사용자가 입력한 인증번호가 서버가 저장해둔 값과 일치하는지 검증한다.
 *
 * [설계 메모 - HttpSession 을 Service 에서 쓰는 것에 대하여]
 *  · 일반적으로 Service 계층은 HTTP 의존에서 자유로운 편이 좋다.
 *  · 그래서 실무에서는 인증번호를 Redis 같은 외부 저장소에 둔다.
 *  · 여기서는 학습 편의를 위해 HttpSession 에 "code_<email>" 키로 저장한다.
 *
 * [핵심 개념]
 *  · JavaMailSender     : Spring Boot 가 제공하는 SMTP 발송용 컴포넌트
 *  · MimeMessage        : 본문/HTML/첨부파일 을 표현하는 표준 이메일 메시지 객체
 *  · MimeMessageHelper  : MimeMessage 를 쉽게 작성하도록 도와주는 헬퍼 (수신자, 제목, 본문 등)
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MailService {

    private final JavaMailSender javaMailSender;
    private final HttpSession session;

    /**
     * 인증번호를 발송한다.
     *
     * [동작 흐름]
     *  1) 6자리 인증번호를 생성한다.
     *  2) MimeMessage 와 Helper 를 준비한다.
     *  3) 받는 사람, 제목, HTML 본문을 세팅한다.
     *  4) SMTP 서버를 통해 메일을 발송한다.
     *  5) 검증 단계에서 쓸 수 있도록 세션에 "code_<email>" 키로 저장한다.
     *
     * @param email 사용자가 입력한 이메일 주소
     */
    public void 인증번호발송(String email) {
        // 1. 인증번호 생성
        String code = MailUtils.generateRandomCode();
        log.debug("이메일 인증번호 생성 (개발용) : {}", code);

        // 2. MimeMessage 준비
        MimeMessage message = javaMailSender.createMimeMessage();

        try {
            // 3. Helper 로 이메일 구성
            //    - 두 번째 파라미터 true : 멀티파트(HTML/첨부 등) 사용을 의미한다.
            //    - 세 번째 파라미터 UTF-8 : 인코딩 지정 (한글 깨짐 방지)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("[MyBlog] 회원가입 이메일 인증번호");
            helper.setText("<h3>인증번호는 [" + code + "] 입니다.</h3>", true);

            // 4. 실제 발송 (application-*.yml 에 설정된 SMTP 정보 사용)
            javaMailSender.send(message);

            // 5. 검증 용도로 세션에 저장
            //    [키 전략] "code_<email>" - 동시에 여러 사람이 가입 시도해도
            //              본인 이메일별로 구분된 인증번호를 저장할 수 있다.
            session.setAttribute("code_" + email, code);
            log.debug("이메일 인증번호 발송 완료");

        } catch (MessagingException e) {
            // 발송 실패 원인 예) SMTP 연결 실패, 앱 비밀번호 오류, 네트워크 끊김 등
            log.error("메일 발송 실패", e);
            throw new Exception400("메일 발송에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 사용자가 입력한 인증번호가 맞는지 검증한다.
     *
     * [동작 흐름]
     *  1) 세션에 저장해둔 인증번호를 꺼낸다.
     *  2) 값이 존재하고 입력값과 같으면 "일회용" 의미로 세션에서 제거하고 true.
     *  3) 그렇지 않으면 false.
     *
     * @param email 검증할 이메일 (세션 키를 찾는 데 사용)
     * @param code  사용자가 화면에서 입력한 인증번호
     * @return 일치 여부
     */
    public boolean 인증번호확인(String email, String code) {
        // 1. 세션에서 기존 인증번호 꺼내기
        String savedCode = (String) session.getAttribute("code_" + email);

        // 2. 존재 & 일치 → 일회용으로 삭제 후 true 반환
        if (savedCode != null && savedCode.equals(code)) {
            session.removeAttribute("code_" + email);
            return true;
        }

        // 3. 불일치 또는 없음
        return false;
    }
}
