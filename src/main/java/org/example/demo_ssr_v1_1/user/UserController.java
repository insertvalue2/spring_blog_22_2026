package org.example.demo_ssr_v1_1.user;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@Controller
public class UserController {

    private final UserService userService;
    private final PurchaseService purchaseService;
    private final PaymentService paymentService;

    /**
     * 결제 내역 목록 조회
     */
    @GetMapping("/user/payment/list")
    public String paymentList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        List<PaymentResponse.ListDTO> paymentList = paymentService.결제내역조회(sessionUser.getId());
        model.addAttribute("paymentList", paymentList);
        return "user/payment-list";
    }

    @GetMapping("/user/purchase/list")
    public String purchaseList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        List<PurchaseResponse.ListDTO> purchaseList = purchaseService.구매내역조회(sessionUser.getId());
        model.addAttribute("purchaseList", purchaseList);
        return "user/purchase-list";
    }


    @GetMapping("/user/point/charge")
    public String chargePointForm(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        model.addAttribute("user", sessionUser);
        return "user/charge-point";
    }



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

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "user/login-form";
    }

    @PostMapping("/login")
    public String loginProc(UserRequest.LoginDTO loginDTO, HttpSession session) {
        try {
            User sessionUser = userService.로그인(loginDTO);
            session.setAttribute("sessionUser", sessionUser);

            return "redirect:/";
        } catch (Exception e) {
            throw new Exception401("아이디 혹시 비밀번호를 확인 하세요");
        }
    }

    @GetMapping("/join")
    public String joinFrom() {
        return "user/join-form";
    }

    @PostMapping("/join")
    public String joinProc(UserRequest.JoinDTO joinDTO, HttpSession session) {
        // 이메일 인증 완료 여부 확인
        // - 세션에 저장된 인증 완료 플래그 확인
        // - Key: "email_verified_" + 이메일
        Boolean isEmailVerified = (Boolean) session.getAttribute("email_verified_" + joinDTO.getEmail());
        
        if (isEmailVerified == null || !isEmailVerified) {
            // 이메일 인증이 완료되지 않은 경우 예외 발생
            throw new org.example.demo_ssr_v1_1._core.errors.exception.Exception400("이메일 인증을 먼저 완료해주세요.");
        }
        
        // 이메일 인증이 완료된 경우 회원가입 처리
        userService.회원가입(joinDTO);
        
        // 회원가입 완료 후 세션에서 인증 플래그 제거 (일회용)
        session.removeAttribute("email_verified_" + joinDTO.getEmail());
        
        return "redirect:/login";
    }
}
