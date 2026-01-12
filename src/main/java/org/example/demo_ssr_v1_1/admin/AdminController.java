package org.example.demo_ssr_v1_1.admin;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.example.demo_ssr_v1_1.refund.RefundResponse;
import org.example.demo_ssr_v1_1.refund.RefundService;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 관리자 전용 페이지 컨트롤러.
 *
 * - /admin/** URI는 WebMvcConfig에서 LoginInterceptor와 AdminInterceptor가 처리합니다.
 * - 로그인 및 관리자 권한 체크는 인터셉터에서 자동으로 처리되므로 컨트롤러에서는 불필요합니다.
 */
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final RefundService refundService;


    @GetMapping("/admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        
        // if(sessionUser == null) {
        //     throw new Exception401("로그인이 필요합니다");
        // }

        // if(!sessionUser.isAdmin()) {
        //     throw new Exception401("관리자만 접근 가능 합니다");
        // }

        model.addAttribute("adminName", sessionUser.getUsername());
        return "admin/dashboard";
    }

    /**
     * 환불 요청 관리 페이지 (관리자용)
     */
    @GetMapping("/admin/refund/list")
    public String refundManagement(Model model) {
        List<RefundResponse.AdminListDTO> refundList = refundService.관리자환불요청목록조회();
        model.addAttribute("refundList", refundList);
        return "admin/admin-refund-list";
    }


    /**
     * 환불 거절 처리
     */
    @PostMapping("/admin/refund/{id}/reject")
    public String rejectRefund(@PathVariable Long id, @RequestParam String rejectReason) {
        refundService.환불거절(id, rejectReason);
        return "redirect:/admin/refund/list";
    }

    /**
     * 환불 승인 처리
     */
    @PostMapping("/admin/refund/{id}/approve")
    public String approveRefund(@PathVariable Long id) {
        refundService.환불승인(id);
        return "redirect:/admin/refund/list";
    }

}


