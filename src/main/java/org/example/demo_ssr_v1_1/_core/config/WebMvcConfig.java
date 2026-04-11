package org.example.demo_ssr_v1_1._core.config;

import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.interceptor.AdminInterceptor;
import org.example.demo_ssr_v1_1._core.interceptor.LoginInterceptor;
import org.example.demo_ssr_v1_1._core.interceptor.SessionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 공통 설정 클래스
 *
 * [이 클래스에서 하는 일]
 * 1. 인터셉터 등록 : 어떤 URL 패턴에 어떤 인터셉터를 끼울지 선언한다.
 * 2. 리소스 핸들러 : /images/** 요청을 프로젝트 루트의 images/ 디렉터리와 연결한다.
 * 3. PasswordEncoder 빈 등록 : BCrypt 해싱에 쓴다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final SessionInterceptor sessionInterceptor;
    private final AdminInterceptor adminInterceptor;

    /**
     * 인터셉터 등록.
     *
     * [등록 순서의 의미]
     * 같은 요청에 여러 인터셉터가 걸려 있을 때, addInterceptor 한 순서대로 preHandle 이 호출된다.
     *   1) SessionInterceptor : 모든 요청에 적용. 뷰 모델에 sessionUser 를 공통 주입.
     *   2) LoginInterceptor   : 보호된 URL 에만 적용. 로그인 여부 체크.
     *   3) AdminInterceptor   : /admin/** 에만 적용. ADMIN 권한 체크.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 모든 요청에 대해 세션 주입 인터셉터 적용
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/**");

        // 2. 로그인이 필요한 URL 묶음
        //    - addPathPatterns   : 검사할 URL
        //    - excludePathPatterns : 검사에서 제외할 URL (로그인/회원가입/정적 리소스 등)
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/board/**", "/user/**", "/reply/**", "/admin/**")
                .excludePathPatterns(
                        // 비회원도 접근해야 하는 URL
                        "/login", "/join", "/logout", "/user/kakao",
                        // 비회원도 읽기는 가능한 게시판 경로
                        "/board/list", "/", "/board/{id:\\d+}",
                        // 정적 리소스
                        "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        // 개발용 H2 콘솔
                        "/h2-console/**"
                );

        // 3. 관리자 전용 URL
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");
    }

    /**
     * 정적 리소스 매핑.
     *
     * [동작 원리]
     *  브라우저가 /images/파일명.jpg 를 요청 -> 실제로는 "프로젝트 루트/images/파일명.jpg" 를 내려준다.
     *  FileUtil 로 저장한 파일을 화면에서 즉시 볼 수 있게 하는 다리 역할.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:images/");
    }

    /**
     * 비밀번호 암호화를 위한 PasswordEncoder 빈 등록.
     *
     * [학습용 용어 정리]
     *
     * 1. 해싱(Hashing)
     *    - 입력값을 고정 길이의 "알 수 없는 문자열" 로 변환하는 단방향 함수.
     *    - 예) "1234" -> "$2a$10$D8...(긴 문자열)"
     *
     * 2. 단방향(One-Way)
     *    - 갈아버린 고기는 원래 스테이크로 되돌릴 수 없다. 해싱도 마찬가지.
     *    - 비밀번호 검사는 "사용자가 입력한 값을 똑같이 해싱해서 결과가 같은지" 만 비교한다.
     *
     * 3. 솔트(Salt)
     *    - 같은 비밀번호를 쓰는 두 사람이 있어도, 각자 다른 랜덤 솔트가 붙기 때문에
     *      저장되는 해시 값이 완전히 달라진다.
     *    - 레인보우 테이블(미리 계산된 해시표) 공격을 막아준다.
     *
     * BCryptPasswordEncoder 는 1~3 을 모두 내부에서 처리해준다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
