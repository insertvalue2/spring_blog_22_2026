package org.example.demo_ssr_v1_1._core.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 세션 사용자 정보를 View 모델에 "공통 주입" 해주는 인터셉터
 *
 * [이 클래스가 필요한 이유]
 * Mustache 화면마다 "{{#sessionUser}} 로그아웃 {{/sessionUser}}" 처럼
 * 로그인 사용자 정보를 써야 할 일이 많다.
 * 매 컨트롤러에서 model.addAttribute("sessionUser", user) 를 반복하는 건 지치므로,
 * postHandle(컨트롤러 실행 후, 뷰 렌더링 전) 에서 한 번에 모델에 집어넣어 준다.
 *
 * [postHandle 타이밍 이해]
 *   preHandle → (컨트롤러 실행) → postHandle → (뷰 렌더링) → afterCompletion
 *                                       ↑
 *                           여기서 modelAndView 에 값을 더 넣을 수 있다.
 *
 * [주의]
 * - @RestController 처럼 뷰를 렌더링하지 않는 경우 modelAndView 는 null 이다.
 * - redirect: 응답도 ModelAndView 가 null 이거나 뷰 이름이 "redirect:..." 이므로 주의.
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {

        // 1. JSON 응답이거나 뷰가 없는 경우 종료 (주입할 곳이 없음)
        if (modelAndView == null) {
            return;
        }

        // 2. 세션을 "만들지 않고" 꺼낸다.
        //    - getSession(false) : 이미 있으면 반환, 없으면 null
        //    - getSession()      : 없으면 새로 생성 → 비로그인 방문자에게도 세션이 생겨 메모리 낭비
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        // 3. 세션에 저장된 사용자 정보를 꺼낸다.
        //    로그인 성공 시 UserController 가 session.setAttribute("sessionUser", user) 로 넣어준다.
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            return;
        }

        // 4. 뷰 모델에 공통 주입.
        //    이제 모든 Mustache 에서 {{sessionUser.username}}, {{#sessionUser.isAdmin}} 같이 사용 가능.
        modelAndView.addObject("sessionUser", sessionUser);
    }
}
