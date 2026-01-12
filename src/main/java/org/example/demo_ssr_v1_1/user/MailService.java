package org.example.demo_ssr_v1_1.user;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.utils.MailUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 메일 발송 서비스
 * 
 * 비즈니스 로직:
 * 1. 인증번호 생성
 * 2. 이메일 발송
 * 3. 세션에 인증번호 저장 (검증용)
 * 
 * ⚠ 주의: Service 레이어에서 HttpSession을 사용하는 것은 일반적으로 권장되지 않지만,
 * 학습 목적으로 간단하게 구현하기 위해 사용합니다.
 * 실무에서는 Redis 같은 인메모리 DB를 사용합니다.
 * 
 * 핵심 개념:
 * 1. JavaMailSender: Spring Boot가 제공하는 메일 발송 인터페이스
 * 2. MimeMessage: 이메일 메시지를 나타내는 객체 (HTML, 첨부파일 지원)
 * 3. MimeMessageHelper: 이메일 작성 편의 클래스
 * 4. HttpSession: 서버에 임시 데이터 저장 (인증번호 저장용)
 */
@RequiredArgsConstructor
@Service
public class MailService {

    // Spring Boot가 제공하는 메일 발송 객체
    // application.yml에 설정된 SMTP 서버 정보를 사용하여 메일 발송
    private final JavaMailSender javaMailSender;
    
    // 세션 객체 (인증번호 저장용)
    // 일반적으로 Service 레이어에서는 HttpSession을 사용하지 않지만, 학습 목적으로 간단하게 구현하기 위해 사용
    private final HttpSession session;


    /**
     * 인증번호 발송하기
     * @param email - 사용자가 입력한 이메일 주소
     */
    public void 인증번호발송(String email) {
        // 1. 인증번호 생성
        String code = MailUtils.generateRandomCode();
        System.out.println("생성된 인증번호: " + code); // 개발용 로그

        // 2. 이메일 전송 내용 설정
        // MimeMessage  / SimpleMailMessage 도 있음 (순수하게 글자만 보낼 때 사용)
        // - 텍스트뿐만 아니라 HTML, 첨부파일 등을 포함할 수 있는 표준 포맷
        // (백지 편지 봉투) - 우편 위치 부터 정확한 포맷 형식을 맞춰야 함
        MimeMessage message = javaMailSender.createMimeMessage();
        
        try {

            // 3. 도우미 객체(Helper) 생성
            // true: "멀티파트(HTML, 파일 등)를 사용하겠다"는 설정
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(email); // 받는 사람 설정
            helper.setSubject("[MyBlog] 회원가입 이메일 인증번호"); // 제목 설정
            // 본문 설정 (HTML 형식)
            helper.setText("<h3>인증번호는 [" + code + "] 입니다.</h3>", true);

            // 4. 메일 발송 (SMTP 서버와 통신)
            // - application.yml에 설정된 구글 SMTP 서버를 통해 전송됨
            javaMailSender.send(message);

            // 5. 세션에 인증번호 저장 (중요!)
            // - 목적: 나중에 사용자가 번호를 입력했을 때 맞는지 확인하기 위해 서버가 기억하고 있어야 함
            // - Key 이름 전략: "code_" + 이메일 (예: "code_test@gmail.com")
            //   -> 동시 접속자가 많아도 이메일 주소로 누구의 인증번호인지 구별할 수 있음
            session.setAttribute("code_" + email, code);
            System.out.println("인증번호 발송 완료: " + code); // 개발용 로그

        } catch (MessagingException e) {
            // 메일 발송 실패 시 예외 처리
            // - SMTP 서버 연결 실패
            // - 인증 실패 (앱 비밀번호 오류 등)
            // - 네트워크 오류
            e.printStackTrace();
            throw new Exception400("메일 발송에 실패했습니다: " + e.getMessage());
        }
    }


    /**
     * [기능 2] 인증번호 검증 메서드
     * * @param email 검증할 이메일 (Key를 찾기 위해 필요)
     * @param code 사용자가 입력한 인증번호
     * @return true(일치), false(불일치)
     */
    public boolean 인증번호확인(String email, String code) {
        // 세션에서 저장된 코드 가져오기
        // Key: "code_" + email (저장할 때와 동일한 키 사용)
        String savedCode = (String) session.getAttribute("code_" + email);

        // 인증번호 비교
        // - savedCode != null: 세션에 인증번호가 저장되어 있음 (세션 만료 안됨)
        // - savedCode.equals(code): 저장된 번호와 사용자가 입력한 번호가 일치
        if (savedCode != null && savedCode.equals(code)) {
            // 인증 성공 시 세션에서 삭제 (일회용)
            // - 같은 인증번호로 다시 인증하는 것을 방지
            // - 메모리 절약
            session.removeAttribute("code_" + email);
            return true;
        }
        
        // 검증 실패
        // - 세션에 인증번호가 없음 (세션 만료 또는 아직 발송 안됨)
        // - 인증번호가 일치하지 않음
        return false;
    }
}
