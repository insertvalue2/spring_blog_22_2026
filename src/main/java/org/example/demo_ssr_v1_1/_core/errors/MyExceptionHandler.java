package org.example.demo_ssr_v1_1._core.errors;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.demo_ssr_v1_1._core.errors.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 전역 예외 처리 클래스 (Global Exception Handler)
 *
 * [핵심 개념]
 * - @ControllerAdvice : 모든 @Controller 에서 던져지는 예외를 한 곳에서 가로채는 "공통 예외 처리기".
 * - @RestControllerAdvice = @ControllerAdvice + @ResponseBody (JSON 응답용. 여기서는 HTML 응답도 있어서 일반 버전 사용).
 * - @ExceptionHandler(타입.class) : 해당 예외 타입(과 그 하위 타입)을 잡는다.
 *
 * [응답 전략 - 학습 예제 단순화]
 * - 4xx(400/401/403) : 작은 HTML(<script>alert...</script>)을 돌려줘서 사용자에게 바로 메시지를 띄운다.
 *   -> Ajax/비-Ajax 구분 없이 학생이 결과를 빠르게 체감할 수 있음.
 * - 4xx(404), 5xx     : 전용 에러 페이지(err/xxx.mustache)로 forward.
 *
 * [학습 포인트]
 * 학생들이 자주 헷갈리는 것 : "왜 어떤 건 페이지로 가고, 어떤 건 alert 인가?"
 *   답) 일관성보다 "학습 편의성" 우선. 실제 운영 코드라면 모두 JSON 또는 모두 에러 페이지로 통일해야 한다.
 */
@ControllerAdvice
@Slf4j
public class MyExceptionHandler {

    // =========================================================================
    // 400 Bad Request - 클라이언트의 잘못된 요청 / 비즈니스 검증 실패
    // =========================================================================
    @ExceptionHandler(Exception400.class)
    @ResponseBody
    public ResponseEntity<String> ex400(Exception400 e, HttpServletRequest request) {
        logException("400", request, e);

        // 1. 메시지가 null 일 수도 있으므로 기본 메시지를 준비한다.
        // 2. JS 문자열 리터럴 안에 들어가므로, 작은따옴표 ' 를 이스케이프 해둔다.
        //    ('를 \' 로 바꿔서 alert('오라클's') 같은 구문 오류를 방지)
        String message = (e.getMessage() != null) ? e.getMessage() : "잘못된 요청입니다";
        String script = buildAlertBackScript(message);

        // 3. 응답 상태코드 400 + HTML 본문
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(script);
    }

    // =========================================================================
    // 401 Unauthorized - 로그인 자체가 안 된 상태
    // =========================================================================
    @ExceptionHandler(Exception401.class)
    public ResponseEntity<String> ex401(Exception401 e, HttpServletRequest request) {
        logException("401", request, e);

        // 1. alert 로 "로그인 해야 합니다" 안내
        // 2. alert 닫히면 /login 으로 강제 이동 (location.href)
        String message = (e.getMessage() != null) ? e.getMessage() : "인증이 필요합니다";
        String escapedMessage = message.replace("'", "\\'");
        String script = "<script>"
                + "alert('" + escapedMessage + "');"
                + "location.href='/login';"
                + "</script>";

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_HTML)
                .body(script);
    }

    // =========================================================================
    // 403 Forbidden - 로그인은 했지만 권한이 없음 (인가 실패)
    // =========================================================================
    @ExceptionHandler(Exception403.class)
    @ResponseBody
    public ResponseEntity<String> ex403(Exception403 e, HttpServletRequest request) {
        logException("403", request, e);

        String message = (e.getMessage() != null) ? e.getMessage() : "접근 권한이 없습니다";
        String script = buildAlertBackScript(message);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.TEXT_HTML)
                .body(script);
    }

    // =========================================================================
    // 404 Not Found - 리소스 없음 (에러 페이지로 forward)
    // =========================================================================
    @ExceptionHandler(Exception404.class)
    public String ex404(Exception404 e, HttpServletRequest request, Model model) {
        logException("404", request, e);
        model.addAttribute("msg", e.getMessage());
        return "err/404";
    }

    // =========================================================================
    // 500 Internal Server Error - 서버 내부 오류
    // =========================================================================
    @ExceptionHandler(Exception500.class)
    public String ex500(Exception500 e, HttpServletRequest request, Model model) {
        logException("500", request, e);
        model.addAttribute("msg", e.getMessage());
        return "err/500";
    }

    // =========================================================================
    // DB 제약조건 위반 (UNIQUE, FK 등) - 400 으로 변환
    // =========================================================================
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseBody
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                               HttpServletRequest request) {
        log.warn("=== DB 제약조건 위반 ===");
        log.warn("요청 URL : {}", request.getRequestURL());
        log.warn("에러 메세지 : {}", e.getMessage());

        // 1. 원인 메시지를 읽어서 사용자 친화적 메시지로 변환
        //    (스프링/하이버네이트의 raw 메시지는 사용자에게 보여주면 안 됨 - 내부 구조 노출 위험)
        String rawMessage = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        String userMessage;
        if (rawMessage.contains("username")) {
            userMessage = "이미 존재하는 사용자 이름입니다";
        } else if (rawMessage.contains("email")) {
            userMessage = "이미 등록된 이메일입니다";
        } else if (rawMessage.contains("foreign key")) {
            userMessage = "관련된 데이터가 있어 삭제할 수 없습니다. (예: 게시글에 댓글이 있는 경우)";
        } else {
            userMessage = "데이터베이스 제약조건 위반이 발생했습니다";
        }

        String script = buildAlertBackScript(userMessage);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body(script);
    }

    // =========================================================================
    // 기타 모든 RuntimeException (최후의 안전장치 #1)
    // =========================================================================
    @ExceptionHandler(RuntimeException.class)
    public String handleRuntimeException(RuntimeException e, HttpServletRequest request, Model model) {
        log.error("=== 예상치 못한 RuntimeException 발생 ===");
        log.error("요청 URL : {}", request.getRequestURL());
        log.error("에러 메세지 : {}", e.getMessage());
        log.error("스택 트레이스:", e);
        model.addAttribute("msg", e.getMessage());
        return "err/500";
    }

    // =========================================================================
    // Error 계열 (NoClassDefFoundError 등 JVM 수준 오류, 최후의 안전장치 #2)
    // =========================================================================
    @ExceptionHandler(Error.class)
    public String handleError(Error e, HttpServletRequest request, Model model) {
        log.error("=== 심각한 오류(Error) 발생 ===");
        log.error("요청 URL : {}", request.getRequestURL());
        log.error("에러 메세지 : {}", e.getMessage());
        log.error("스택 트레이스:", e);

        if (e instanceof NoClassDefFoundError) {
            model.addAttribute("msg",
                    "클래스를 찾을 수 없습니다: " + e.getMessage()
                            + " (빌드 정리 후 재컴파일이 필요할 수 있습니다)");
        } else {
            model.addAttribute("msg", "심각한 오류가 발생했습니다: " + e.getMessage());
        }
        return "err/500";
    }

    // =========================================================================
    // Throwable 최상위 - 정말 아무것도 못 잡았을 때의 최후의 안전장치 #3
    // =========================================================================
    @ExceptionHandler(Throwable.class)
    public String handleThrowable(Throwable e, HttpServletRequest request, Model model) {
        log.error("=== 처리되지 않은 예외/오류 발생 ===");
        log.error("요청 URL : {}", request.getRequestURL());
        log.error("에러 메세지 : {}", e.getMessage());
        log.error("스택 트레이스:", e);

        String safeMessage = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
        model.addAttribute("msg", "예상치 못한 오류가 발생했습니다: " + safeMessage);
        return "err/500";
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    /**
     * alert 메시지를 띄우고 이전 페이지로 돌아가는 자바스크립트를 생성한다.
     *
     * 학생들이 자주 하는 실수 :
     *   - e.getMessage() 에 ' (작은따옴표) 가 들어있으면 alert('...') 구문이 깨진다.
     *   - 따라서 치환 처리가 반드시 필요하다.
     */
    private String buildAlertBackScript(String message) {
        String escaped = message.replace("'", "\\'");
        return "<script>alert('" + escaped + "');history.back();</script>";
    }

    /**
     * 공통 에러 로그 포맷.
     */
    private void logException(String statusCode, HttpServletRequest request, Throwable e) {
        log.warn("=== {} 에러 발생 ===", statusCode);
        log.warn("요청 URL : {}", request.getRequestURL());
        log.warn("에러 메세지 : {}", e.getMessage());
        log.warn("예외 클래스 : {}", e.getClass().getSimpleName());
    }
}
