package org.example.demo_ssr_v1_1.refund;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception401;
import org.example.demo_ssr_v1_1.payment.Payment;
import org.example.demo_ssr_v1_1.payment.PaymentResponse;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class RefundController {

    private final RefundService refundService;

    /**
     * 환불 요청 화면
     */
    @GetMapping("/refund/request/{paymentId}")
    public String refundRequestForm(@PathVariable Long paymentId, Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        // TODO - 추후 수정 (인터셉터 URL 추가)
        if (sessionUser == null) {
            throw new Exception401("로그인이 필요합니다");
        }

        // 서비스에서 검증 및 조회 처리 (컨트롤러 코드가 훨씬 깔끔해졌습니다!)
        Payment payment = refundService.환불요청화면검증(paymentId, sessionUser.getId());

        // DTO로 변환하여 뷰에 전달
        PaymentResponse.ListDTO paymentDTO = new PaymentResponse.ListDTO(payment);
        model.addAttribute("payment", paymentDTO);
        return "refund/request-form";
    }

    /**
     * 환불 요청 처리
     */
    @PostMapping("/refund/request")
    public String refundRequest(RefundResponse.RequestDTO requestDTO, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            throw new Exception401("로그인이 필요합니다");
        }

        // 서비스 호출
        refundService.환불요청(sessionUser.getId(), requestDTO);

        return "redirect:/refund/list";
    }


    /**
     * 환불 요청 목록 조회 (사용자용)
     */
    @GetMapping("/refund/list")
    public String refundList(Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        if (sessionUser == null) {
            throw new Exception401("로그인이 필요합니다");
        }

        List<RefundResponse.ListDTO> refundList = refundService.환불요청목록조회(sessionUser.getId());
        model.addAttribute("refundList", refundList);
        return "refund/list";
    }

}
