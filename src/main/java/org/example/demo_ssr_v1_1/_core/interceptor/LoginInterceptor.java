package org.example.demo_ssr_v1_1._core.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception401;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 인증(Authentication) 인터셉터
 *
 * [이 클래스의 역할]
 * - 컨트롤러 메서드가 실행되기 "이전(preHandle)" 에 가로채서,
 *   세션에 sessionUser 가 담겨 있는지(= 로그인 상태인지) 검사한다.
 * - 로그인 안 된 사용자가 보호된 URL(/board/save, /user/update 등)에 접근하면
 *   Exception401 을 던져 /login 으로 유도한다.
 *
 * [인증 vs 인가 - 학생이 가장 많이 헷갈리는 부분]
 *  · 인증(Authentication) : "너 누구야?" -> 로그인이 됐는지.  (이 클래스의 관심사)
 *  · 인가(Authorization)  : "너 이거 할 수 있어?" -> 권한(USER/ADMIN)이 맞는지. (AdminInterceptor)
 *
 * [어디에 등록되나]
 * WebMvcConfig.addInterceptors() 에서 addPathPatterns/excludePathPatterns 로
 * "어느 URL 에 적용할지"를 정해준다. 이 파일에는 URL 매칭 정보가 없다는 점에 주의.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 컨트롤러 진입 전에 호출된다.
     *
     * @return true  -> 컨트롤러 메서드 실행 허용
     *         false -> 컨트롤러 메서드 실행 차단 (여기서는 예외를 던지므로 false 를 반환할 일이 없다)
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 세션을 꺼낸다.
        //    getSession(false) : 세션이 없으면 null 을 반환한다. (새로 만들지 않는다)
        //    getSession()      : 세션이 없으면 "새로 만들어서" 반환한다.
        //    -> 비로그인 사용자에게 쓸데없는 세션을 만들지 않기 위해 false 를 사용한다.
        HttpSession session = request.getSession(false);

        // 2. 세션이 아예 없거나, 세션에 sessionUser 가 없으면 "로그인 안 한 상태"다.
        User sessionUser = (session != null) ? (User) session.getAttribute("sessionUser") : null;

        // 3. 로그인 안 됐으면 401 예외를 던진다. (MyExceptionHandler 가 받아서 /login 으로 이동시킴)
        if (sessionUser == null) {
            throw new Exception401("로그인 먼저 해주세요");
        }

        // 4. 로그인 상태 확인 완료 -> 컨트롤러 실행 허용
        return true;
    }
}
