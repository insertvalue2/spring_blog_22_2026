package org.example.demo_ssr_v1_1.refund;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1.payment.Payment;
import org.example.demo_ssr_v1_1.payment.PaymentResponse;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * 환불(Refund) SSR 컨트롤러 (사용자 영역)
 *
 * [담당 URL]
 *  · GET  /refund/request/{paymentId} : 환불 요청 화면 (검증 통과 시 폼 노출)
 *  · POST /refund/request              : 환불 요청 등록
 *  · GET  /refund/list                 : 내 환불 요청 목록
 *
 * [로그인 처리]
 *  · /refund/** 은 LoginInterceptor 에서 보호해야 한다.
 *    (현재 인터셉터 설정에 /refund 가 빠져 있을 수 있으니 WebMvcConfig 확인 필요)
 */
@RequiredArgsConstructor
@Controller
public class RefundController {

    private final RefundService refundService;

    /**
     * 환불 요청 화면.
     *
     * [동작 흐름]
     *  1) 세션 사용자 확인 (없으면 null 로 진행 → 아래 Service 에서 검증 실패 시 403)
     *  2) Service 가 본인 결제 여부 / 상태 / 중복 요청 까지 모두 검증한 뒤 Payment 반환
     *  3) 화면에 뿌릴 DTO 로 변환 후 렌더링
     */
    @GetMapping("/refund/request/{paymentId}")
    public String refundRequestForm(@PathVariable Long paymentId, Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");

        // Service 에 검증을 맡긴다 (컨트롤러는 얇게 유지)
        Payment payment = refundService.환불요청화면검증(paymentId, sessionUser.getId());

        // 엔티티를 그대로 내려보내지 않고 DTO 로 감싼다
        PaymentResponse.ListDTO paymentDTO = new PaymentResponse.ListDTO(payment);
        model.addAttribute("payment", paymentDTO);
        return "refund/request-form";
    }

    /**
     * 환불 요청 등록.
     *
     * [동작 흐름]
     *  1) 세션 사용자 확인
     *  2) Service 에 위임 (상태/중복 검증 + 저장)
     *  3) 내 환불 목록 페이지로 리다이렉트
     */
    @PostMapping("/refund/request")
    public String refundRequest(RefundResponse.RequestDTO requestDTO, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        refundService.환불요청(sessionUser.getId(), requestDTO);
        return "redirect:/refund/list";
    }

    /**
     * 내가 요청한 환불 목록.
     */
    @GetMapping("/refund/list")
    public String refundList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        List<RefundResponse.ListDTO> refundList = refundService.환불요청목록조회(sessionUser.getId());
        model.addAttribute("refundList", refundList);
        return "refund/list";
    }
}
