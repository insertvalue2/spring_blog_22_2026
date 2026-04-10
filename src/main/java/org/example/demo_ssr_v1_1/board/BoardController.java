package org.example.demo_ssr_v1_1.board;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1.purchase.PurchaseService;
import org.example.demo_ssr_v1_1.reply.ReplyResponse;
import org.example.demo_ssr_v1_1.reply.ReplyService;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 게시글 SSR 컨트롤러
 *
 * [컨트롤러의 책임]
 *  1. HTTP 요청 파라미터를 DTO 로 받는다.
 *  2. 세션에서 로그인 사용자를 꺼낸다.
 *  3. Service 에 비즈니스 로직을 위임한다.
 *  4. 결과를 Model 에 담거나 redirect 한다.
 *
 * [LoginInterceptor 동작 방식]
 *  · /board/save, /board/{id}/update 등은 WebMvcConfig 에서 로그인 필수로 설정돼 있다.
 *  · 따라서 컨트롤러 진입 시점에는 이미 로그인이 보장돼 있다.
 *  · /board/{id} 상세 조회는 "로그인 안 해도 볼 수 있음" 이므로 sessionUser null 체크가 필요하다.
 */
@RequiredArgsConstructor
@Controller
public class BoardController {

    private final BoardService boardService;
    private final ReplyService replyService;
    private final PurchaseService purchaseService;

    // =========================================================================
    // 목록 + 검색
    // =========================================================================

    /**
     * 게시글 목록 화면.
     *
     * [동작 흐름]
     *  1) 사용자가 보는 page 는 1-based 지만, Pageable 은 0-based 이므로 -1 변환한다.
     *  2) keyword, size 는 기본값이 있다.
     *  3) Service 가 PageDTO 를 돌려주면 그대로 모델에 담는다.
     *  4) 검색어도 함께 내려보내 페이지 이동 시에도 검색어가 유지되도록 한다.
     */
    @GetMapping({"/board/list", "/"})
    public String boardList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String keyword,
            Model model) {

        // 1. 1-based → 0-based 변환
        int pageIndex = Math.max(0, page - 1);

        // 2. Service 위임
        BoardResponse.PageDTO boardPage = boardService.게시글목록조회(pageIndex, size, keyword);

        // 3. 뷰 모델 세팅 (검색어는 null 이면 빈 문자열)
        model.addAttribute("boardPage", boardPage);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        return "board/list";
    }

    // =========================================================================
    // 상세
    // =========================================================================

    /**
     * 게시글 상세.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회 (비로그인 허용이므로 null 가능)
     *  2) Service 에 상세 조회 위임 (구매 여부 포함)
     *  3) "현재 글이 내 것인지" 여부를 Mustache 에서 쓸 수 있도록 계산해 모델에 담는다
     *  4) 댓글 목록도 함께 조회해 모델에 담는다
     */
    @GetMapping("/board/{id}")
    public String detail(@PathVariable Long id, Model model, HttpSession session) {
        // 1. 세션 사용자 (비회원이면 null)
        User sessionUser = (User) session.getAttribute("sessionUser");
        Long sessionUserId = (sessionUser != null) ? sessionUser.getId() : null;

        // 2. 상세 조회
        BoardResponse.DetailDTO board = boardService.게시글상세조회(id, sessionUserId);

        // 3. 본인 글 여부 계산 (수정/삭제 버튼 노출에 사용)
        boolean isOwner = sessionUser != null
                && board.getUserId() != null
                && board.getUserId().equals(sessionUser.getId());

        // 4. 댓글 목록 조회
        List<ReplyResponse.ListDTO> replyList = replyService.댓글목록조회(id, sessionUserId);

        model.addAttribute("board", board);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("replyList", replyList);
        return "board/detail";
    }

    // =========================================================================
    // 작성
    // =========================================================================

    @GetMapping("/board/save")
    public String saveForm() {
        // 인증은 LoginInterceptor 가 이미 처리했으므로 여기서는 뷰만 반환
        return "board/save-form";
    }

    /**
     * 게시글 작성 처리.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회
     *  2) Service 에 작성 위임 (DTO + 세션 사용자 전달)
     *  3) 작성 완료 후 홈으로 이동
     */
    @PostMapping("/board/save")
    public String saveProc(BoardRequest.SaveDTO saveDTO, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        boardService.게시글작성(saveDTO, sessionUser);
        return "redirect:/";
    }

    // =========================================================================
    // 수정
    // =========================================================================

    /**
     * 게시글 수정 폼.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회
     *  2) Service 에서 본인 글만 꺼내온다 (아니면 403)
     *  3) 폼 DTO 를 모델에 담아 뷰 렌더링
     */
    @GetMapping("/board/{id}/update")
    public String updateForm(@PathVariable Long id, Model model, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        BoardResponse.UpdateFormDTO board = boardService.게시글수정화면(id, sessionUser.getId());
        model.addAttribute("board", board);
        return "board/update-form";
    }

    /**
     * 게시글 수정 처리.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회
     *  2) Service 에 위임 (본인 검증 + 더티 체킹 UPDATE)
     *  3) 완료 후 목록으로
     */
    @PostMapping("/board/{id}/update")
    public String updateProc(@PathVariable Long id,
                             BoardRequest.UpdateDTO updateDTO,
                             HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        boardService.게시글수정(id, updateDTO, sessionUser.getId());
        return "redirect:/board/list";
    }

    // =========================================================================
    // 삭제
    // =========================================================================

    /**
     * 게시글 삭제 처리.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회
     *  2) Service 에 삭제 위임 (본인 검증 + 댓글 선삭제 + 게시글 삭제)
     */
    @PostMapping("/board/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        boardService.게시글삭제(id, sessionUser.getId());
        return "redirect:/";
    }

    // =========================================================================
    // 유료글 구매
    // =========================================================================

    /**
     * 유료 게시글 구매.
     *
     * [동작 흐름]
     *  1) 세션 사용자 조회
     *  2) PurchaseService 에 위임 (포인트 차감 + Purchase 저장, 트랜잭션)
     *  3) 구매 완료 후 상세 페이지로 이동 (본문이 풀려 보이게 됨)
     */
    @PostMapping("/board/{id}/purchase")
    public String purchase(@PathVariable Long id, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        purchaseService.구매하기(id, sessionUser.getId());
        return "redirect:/board/" + id;
    }
}
