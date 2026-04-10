package org.example.demo_ssr_v1_1.user;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception401;
import org.example.demo_ssr_v1_1.payment.PaymentResponse;
import org.example.demo_ssr_v1_1.payment.PaymentService;
import org.example.demo_ssr_v1_1.purchase.PurchaseResponse;
import org.example.demo_ssr_v1_1.purchase.PurchaseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 사용자 관련 HTML(SSR) 컨트롤러
 *
 * [담당 URL 요약]
 *  · GET  /login, POST /login            : 로그인
 *  · GET  /logout                        : 로그아웃
 *  · GET  /join,  POST /join             : 회원가입
 *  · GET  /user/kakao                    : 카카오 OAuth 콜백
 *  · GET  /user/detail                   : 마이페이지 (내 정보)
 *  · GET  /user/update, POST /user/update: 회원정보 수정
 *  · POST /user/profile-image/delete     : 프로필 이미지 삭제
 *  · GET  /user/point/charge             : 포인트 충전 화면
 *  · GET  /user/payment/list             : 결제 내역
 *  · GET  /user/purchase/list            : 구매 내역
 *
 * [컨트롤러의 책임]
 *  1. HTTP 요청을 DTO 로 매핑받는다.
 *  2. 세션에서 로그인 사용자 정보를 꺼낸다.
 *  3. Service 에 비즈니스 로직을 위임한다.
 *  4. 결과를 Model 에 담거나 redirect 로 응답한다.
 *  ※ 비즈니스 로직 자체(검증, DB 조작)는 Service 에서 처리해야 한다.
 */
@RequiredArgsConstructor
@Controller
public class UserController {

    private final UserService userService;
    private final PurchaseService purchaseService;
    private final PaymentService paymentService;

    // =========================================================================
    // 로그인 / 로그아웃
    // =========================================================================

    @GetMapping("/login")
    public String loginForm() {
        return "user/login-form";
    }

    /**
     * 로그인 처리.
     *
     * [동작 흐름]
     *  1) UserService 가 DTO 를 받아 사용자 조회 + 비밀번호 매칭까지 수행한다.
     *  2) 성공 시 세션에 sessionUser 를 저장한다.
     *  3) 실패 시 Exception401 로 변환하여 전역 예외 처리기가 /login 으로 돌려보낸다.
     */
    @PostMapping("/login")
    public String loginProc(UserRequest.LoginDTO loginDTO, HttpSession session) {
        try {
            // 1. Service 에 로그인 위임
            User sessionUser = userService.로그인(loginDTO);
            // 2. 세션 저장
            session.setAttribute("sessionUser", sessionUser);
            // 3. 메인 페이지로 이동
            return "redirect:/";
        } catch (Exception e) {
            // 4. 어떤 이유든 실패하면 "아이디 혹은 비밀번호를 확인하세요" 로 통일 (정보 노출 방지)
            throw new Exception401("아이디 혹은 비밀번호를 확인 하세요");
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // session.invalidate() 는 세션 전체를 파기한다. sessionUser 포함 모든 attribute 가 사라짐.
        session.invalidate();
        return "redirect:/";
    }

    // =========================================================================
    // 회원가입
    // =========================================================================

    @GetMapping("/join")
    public String joinForm() {
        return "user/join-form";
    }

    /**
     * 회원가입 처리.
     *
     * [동작 흐름 - 이메일 인증 이중 검증]
     *  1) 세션에 저장된 "email_verified_<email>" 플래그가 true 인지 확인한다.
     *     → 인증 없이 JavaScript 를 조작한 우회 시도를 서버에서 한 번 더 차단한다.
     *  2) 플래그가 없으면 Exception400 으로 즉시 거부.
     *  3) 플래그가 있으면 Service 에 회원가입 위임.
     *  4) 성공 후 플래그는 "일회용" 으로 제거한다.
     */
    @PostMapping("/join")
    public String joinProc(UserRequest.JoinDTO joinDTO, HttpSession session) {
        // 1. 세션 플래그 확인
        Boolean isEmailVerified = (Boolean) session.getAttribute("email_verified_" + joinDTO.getEmail());
        if (isEmailVerified == null || !isEmailVerified) {
            throw new Exception400("이메일 인증을 먼저 완료해주세요.");
        }

        // 2. 회원가입 위임
        userService.회원가입(joinDTO);

        // 3. 일회용 플래그 제거
        session.removeAttribute("email_verified_" + joinDTO.getEmail());

        // 4. 로그인 화면으로
        return "redirect:/login";
    }

    // =========================================================================
    // 카카오 소셜 로그인
    // =========================================================================

    /**
     * 카카오에서 발급해준 인가 코드를 받아 로그인/회원가입 처리.
     *
     * [동작 흐름]
     *  1) URL 쿼리에서 code 파라미터를 받는다.
     *  2) UserService 가 내부적으로
     *     (1) 토큰 발급 → (2) 프로필 조회 → (3) 기존 회원이면 로그인, 없으면 자동 가입
     *     까지 수행한 뒤 User 를 돌려준다.
     *  3) 세션에 저장하고 메인으로 이동.
     */
    @GetMapping("/user/kakao")
    public String kakaoCallback(@RequestParam(name = "code") String code, HttpSession session) {
        try {
            User sessionUser = userService.카카오소셜로그인(code);
            session.setAttribute("sessionUser", sessionUser);
            return "redirect:/";
        } catch (Exception e) {
            throw new Exception401("소셜로그인 실패");
        }
    }

    // =========================================================================
    // 마이페이지 / 회원정보 수정
    // =========================================================================

    @GetMapping("/user/detail")
    public String detailForm(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        User user = userService.회원정보수정화면(sessionUser.getId());
        model.addAttribute("user", user);
        return "user/detail";
    }

    @GetMapping("/user/update")
    public String updateForm(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        User user = userService.회원정보수정화면(sessionUser.getId());
        model.addAttribute("user", user);
        return "user/update-form";
    }

    /**
     * 회원정보 수정 처리.
     *
     * [동작 흐름]
     *  1) Service 에 수정 위임 → 새 User 반환
     *  2) 세션의 sessionUser 도 최신 상태로 갱신 (이름/이미지 변경 등이 뷰에 반영되도록)
     *  3) 실패 시 수정 폼으로 되돌린다.
     */
    @PostMapping("/user/update")
    public String updateProc(UserRequest.UpdateDTO updateDTO, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        try {
            User updateUser = userService.회원정보수정(updateDTO, sessionUser.getId());
            session.setAttribute("sessionUser", updateUser);
            return "redirect:/user/detail";
        } catch (Exception e) {
            return "user/update-form";
        }
    }

    /**
     * 프로필 이미지 삭제.
     *
     * [동작 흐름]
     *  1) Service 에 삭제 위임 (디스크 파일 삭제 + DB 필드 null 처리)
     *  2) 세션 사용자 정보도 갱신 (이미지가 사라진 상태 반영)
     */
    @PostMapping("/user/profile-image/delete")
    public String deleteProfileImage(HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        try {
            User updateUser = userService.프로필이미지삭제(sessionUser.getId());
            session.setAttribute("sessionUser", updateUser);
            return "redirect:/user/detail";
        } catch (Exception e) {
            return "redirect:/user/detail";
        }
    }

    // =========================================================================
    // 포인트 / 결제 / 구매 내역
    // =========================================================================

    /** 포인트 충전 폼 화면 */
    @GetMapping("/user/point/charge")
    public String chargePointForm(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        model.addAttribute("user", sessionUser);
        return "user/charge-point";
    }

    /** 결제 내역 목록 (본인 것만) */
    @GetMapping("/user/payment/list")
    public String paymentList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        List<PaymentResponse.ListDTO> paymentList = paymentService.결제내역조회(sessionUser.getId());
        model.addAttribute("paymentList", paymentList);
        return "user/payment-list";
    }

    /** 구매 내역 목록 (본인이 구매한 유료 게시글) */
    @GetMapping("/user/purchase/list")
    public String purchaseList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        List<PurchaseResponse.ListDTO> purchaseList = purchaseService.구매내역조회(sessionUser.getId());
        model.addAttribute("purchaseList", purchaseList);
        return "user/purchase-list";
    }
}
