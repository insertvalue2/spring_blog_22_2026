package org.example.demo_ssr_v1_1.reply;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Reply(댓글) SSR 컨트롤러
 *
 * [담당 URL]
 *  · POST /reply/save         : 댓글 작성
 *  · POST /reply/{id}/delete  : 댓글 삭제
 *
 * [LoginInterceptor 통과 가정]
 *  · /reply/** 은 WebMvcConfig 에서 로그인 필수로 설정돼 있으므로
 *    여기서 sessionUser null 체크를 반복할 필요가 없다.
 */
@RequiredArgsConstructor
@Controller
public class ReplyController {

    private final ReplyService replyService;

    /**
     * 댓글 작성.
     *
     * [동작 흐름]
     *  1) 세션에서 로그인 사용자 꺼내기
     *  2) Service 에 userId 만 전달 (엔티티 통째로 넘기면 detached 이슈 발생 가능)
     *  3) 작성 후 원래 게시글 상세 페이지로 리다이렉트
     */
    @PostMapping("/reply/save")
    public String saveProc(ReplyRequest.SaveDTO saveDTO, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        replyService.댓글작성(saveDTO, sessionUser.getId());
        return "redirect:/board/" + saveDTO.getBoardId();
    }

    /**
     * 댓글 삭제.
     *
     * [동작 흐름]
     *  1) 세션에서 로그인 사용자 꺼내기
     *  2) Service 에 id + userId 를 전달
     *     · Service 가 본인 여부 검증 후 댓글을 삭제하고, 해당 댓글이 속한 게시글 id 를 반환한다.
     *  3) 그 게시글 상세 페이지로 리다이렉트
     */
    @PostMapping("/reply/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        User sessionUser = (User) session.getAttribute("sessionUser");
        Long boardId = replyService.댓글삭제(id, sessionUser.getId());
        return "redirect:/board/" + boardId;
    }
}
