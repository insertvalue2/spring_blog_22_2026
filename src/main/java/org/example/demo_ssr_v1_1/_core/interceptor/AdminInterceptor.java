package org.example.demo_ssr_v1_1._core.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 인가(Authorization) 인터셉터
 *
 * [이 클래스의 역할]
 * /admin/** 로 들어오는 요청에 대해 "사용자가 ADMIN 권한을 가졌는지" 검사한다.
 *
 * [LoginInterceptor 와의 관계 - 실행 순서가 중요]
 *   WebMvcConfig 에서 등록 순서: SessionInterceptor → LoginInterceptor → AdminInterceptor
 *   1) SessionInterceptor  : 뷰에 sessionUser 주입 (postHandle)
 *   2) LoginInterceptor    : "로그인 됐니?" 체크
 *   3) AdminInterceptor    : "ADMIN 이니?" 체크
 *   즉, 이 인터셉터가 실행된 시점에는 이미 로그인은 보장돼 있다.
 *   다만 "혹시 등록 순서 실수" 같은 사고를 막기 위해 한 번 더 null 체크를 해둔다(방어적 프로그래밍).
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 세션을 꺼낸다. (이미 LoginInterceptor 가 있으므로 null 이 나올 일은 거의 없다)
        HttpSession session = request.getSession(false);
        User sessionUser = (session != null) ? (User) session.getAttribute("sessionUser") : null;

        // 2. [방어적 체크] 혹시라도 로그인 인터셉터가 제외된 경로라면 여기서도 한 번 더 잡아준다.
        //    인터셉터 등록 순서/패턴 실수로 발생할 수 있는 보안 구멍을 막는 안전장치.
        if (sessionUser == null) {
            throw new Exception403("로그인이 필요합니다");
        }

        // 3. ADMIN 권한 검사
        //    User.isAdmin() 은 내부적으로 roles 컬렉션을 순회해 ADMIN 이 있는지 확인한다.
        if (!sessionUser.isAdmin()) {
            throw new Exception403("관리자 권한이 필요합니다");
        }

        // 4. 검사 통과 -> 컨트롤러 실행 허용
        return true;
    }
}
