package org.example.demo_ssr_v1_1.reply;

import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1.board.Board;
import org.example.demo_ssr_v1_1.board.BoardRepository;
import org.example.demo_ssr_v1_1.user.User;
import org.example.demo_ssr_v1_1.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reply 도메인 Service (비즈니스 계층)
 *
 * [학습 포인트 - 세션의 User 는 "detached" 일 수 있다]
 *  · 로그인 직후 session.setAttribute("sessionUser", user) 로 저장한 User 는
 *    다음 요청이 오는 시점에는 이미 영속성 컨텍스트에 없는 "분리 상태(detached)" 이다.
 *  · 그걸 그대로 reply.user 에 꽂으면, 상황에 따라 JPA 가 새 INSERT 를 시도하거나
 *    LazyInitializationException 을 던질 수 있다.
 *  · 그래서 "현재 트랜잭션 안에서" user 를 다시 조회해서 붙이는 패턴을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ReplyService {

    private final ReplyRepository replyRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // 댓글 목록
    // =========================================================================

    /**
     * 특정 게시글의 댓글 목록을 조회한다.
     *
     * [동작 흐름]
     *  1) JOIN FETCH 쿼리로 Reply + User + Board 를 한 번에 로딩
     *  2) 각 Reply 를 ListDTO 로 변환 (세션 사용자와 비교해 isOwner 계산)
     *
     * @param boardId       게시글 id
     * @param sessionUserId 현재 로그인 사용자 id (비로그인이면 null)
     */
    @Transactional(readOnly = true)
    public List<ReplyResponse.ListDTO> 댓글목록조회(Long boardId, Long sessionUserId) {
        // 1. DB 조회 (N+1 방지 목적의 JOIN FETCH)
        List<Reply> replyList = replyRepository.findByBoardIdWithUser(boardId);

        // 2. Entity → DTO 변환 (트랜잭션 안에서 수행)
        return replyList.stream()
                .map(reply -> new ReplyResponse.ListDTO(reply, sessionUserId))
                .toList();
    }

    // =========================================================================
    // 댓글 작성
    // =========================================================================

    /**
     * 댓글 작성.
     *
     * [동작 흐름]
     *  1) DTO 검증
     *  2) board_id 로 게시글 존재 여부 확인 (없으면 404)
     *  3) userId 로 "현재 트랜잭션 내" User 를 다시 조회 (detached 주의)
     *  4) DTO → Entity 변환 후 INSERT
     */
    @Transactional
    public Reply 댓글작성(ReplyRequest.SaveDTO saveDTO, Long userId) {
        // 1. DTO 검증
        saveDTO.validate();

        // 2. 대상 게시글 조회
        Board board = boardRepository.findById(saveDTO.getBoardId())
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 3. 트랜잭션 안에서 User 재조회 (영속 상태로 만들어 안전하게 연관관계 설정)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 4. 엔티티 변환 후 저장
        Reply reply = saveDTO.toEntity(board, user);
        return replyRepository.save(reply);
    }

    // =========================================================================
    // 댓글 단건 조회 (필요 시)
    // =========================================================================

    @Transactional(readOnly = true)
    public Reply 댓글조회(Long id) {
        return replyRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("댓글을 찾을 수 없습니다"));
    }

    // =========================================================================
    // 댓글 삭제
    // =========================================================================

    /**
     * 댓글 삭제.
     *
     * [동작 흐름]
     *  1) 댓글 조회 (작성자/게시글 JOIN FETCH)
     *  2) 본인 여부 확인 (아니면 403)
     *  3) 게시글 id 를 미리 저장 (삭제 후 리다이렉트 용도)
     *  4) 댓글 삭제
     *  5) 게시글 id 반환
     */
    @Transactional
    public Long 댓글삭제(Long id, Long userId) {
        Reply reply = replyRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("댓글을 찾을 수 없습니다"));

        if (!reply.isOwner(userId)) {
            throw new Exception403("댓글 삭제 권한이 없습니다");
        }

        Long boardId = reply.getBoard().getId();
        replyRepository.deleteById(id);
        return boardId;
    }
}
