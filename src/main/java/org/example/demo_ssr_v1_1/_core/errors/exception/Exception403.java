package org.example.demo_ssr_v1_1._core.errors.exception;

/**
 * 403 Forbidden 커스텀 예외처리 클래스
 *
 * 인가(Authorization) 실패 시 던지는 예외.
 * - 401(Unauthorized)과 혼동하지 말 것.
 *   · 401 = "너 누구야?" (로그인 자체가 안 된 상태)
 *   · 403 = "너 누군지는 알겠는데, 이 자원은 접근 권한이 없어."
 * - 사용 예: 본인이 아닌 게시글 수정/삭제 시도, USER 권한으로 /admin/** 접근 시도.
 */
public class Exception403 extends RuntimeException {
    public Exception403(String msg) {
        super(msg);
    }
}
