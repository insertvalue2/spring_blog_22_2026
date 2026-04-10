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
 * 관리자 전용 SSR 컨트롤러
 *
 * [담당 URL]
 *  · GET  /admin/dashboard           : 관리자 메인(대시보드)
 *  · GET  /admin/refund/list         : 환불 요청 관리
 *  · POST /admin/refund/{id}/reject  : 환불 거절
 *  · POST /admin/refund/{id}/approve : 환불 승인 (PortOne cancel API 호출)
 *
 * [권한 처리]
 *  · WebMvcConfig 에서 /admin/** 에 LoginInterceptor + AdminInterceptor 를 함께 등록한다.
 *  · 따라서 이 컨트롤러의 메서드들은 "이미 로그인 + ADMIN 권한이 확인된 상태" 에서만 실행된다.
 *  · 컨트롤러 본체에서는 권한 체크 코드를 다시 쓰지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final RefundService refundService;

    // =========================================================================
    // 대시보드
    // =========================================================================

    /**
     * 관리자 메인 화면.
     *
     * [동작 흐름]
     *  1) 세션에서 관리자 정보 꺼내기
     *  2) 화면에 표시할 관리자 이름만 뷰 모델에 담기
     */
    @GetMapping("/admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        model.addAttribute("adminName", sessionUser.getUsername());
        return "admin/dashboard";
    }

    // =========================================================================
    // 환불 관리
    // =========================================================================

    /**
     * 환불 요청 목록 화면.
     *
     * [동작 흐름]
     *  1) Service 에서 전체 환불 요청 목록 조회 (승인/거절/대기 모두 포함)
     *  2) 뷰 모델에 담아 렌더링
     */
    @GetMapping("/admin/refund/list")
    public String refundManagement(Model model) {
        List<RefundResponse.AdminListDTO> refundList = refundService.관리자환불요청목록조회();
        model.addAttribute("refundList", refundList);
        return "admin/admin-refund-list";
    }

    /**
     * 환불 거절 처리.
     *
     * [동작 흐름]
     *  1) Service 가 상태를 REJECTED 로 전이하고 사유를 기록
     *  2) 목록 화면으로 리다이렉트
     */
    @PostMapping("/admin/refund/{id}/reject")
    public String rejectRefund(@PathVariable Long id, @RequestParam String rejectReason) {
        refundService.환불거절(id, rejectReason);
        return "redirect:/admin/refund/list";
    }

    /**
     * 환불 승인 처리.
     *
     * [동작 흐름]
     *  1) Service 가 "검증 → 포트원 취소 API → DB 상태 변경" 을 한 트랜잭션으로 수행
     *  2) 성공하면 목록으로 리다이렉트
     */
    @PostMapping("/admin/refund/{id}/approve")
    public String approveRefund(@PathVariable Long id) {
        refundService.환불승인(id);
        return "redirect:/admin/refund/list";
    }
}
