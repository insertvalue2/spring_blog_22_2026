package org.example.demo_ssr_v1_1.board;

import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception500;
import org.example.demo_ssr_v1_1.purchase.PurchaseService;
import org.example.demo_ssr_v1_1.reply.ReplyRepository;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board 도메인 Service (비즈니스 계층)
 *
 * [계층 구조]
 *   Controller (HTTP/DTO)
 *        │
 *        ▼
 *   Service (비즈니스 규칙 + 트랜잭션 경계)   ← 이 클래스
 *        │
 *        ▼
 *   Repository (DB 접근)
 *        │
 *        ▼
 *   Database
 *
 * [@Transactional 규칙]
 *  · 쓰기 작업 → @Transactional
 *  · 읽기 전용 → @Transactional(readOnly = true)
 *  · 읽기 전용으로 선언하면 Hibernate 가 dirty checking 을 생략해 약간의 성능 이점이 있다.
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final ReplyRepository replyRepository;
    private final PurchaseService purchaseService;

    // =========================================================================
    // 목록 조회 (페이징 + 검색)
    // =========================================================================

    /**
     * 게시글 목록 조회 (페이징 + 선택적 검색).
     *
     * [동작 흐름]
     *  1) 페이지/크기를 안전한 범위로 보정한다.
     *     · page  : 0 미만이면 0 으로
     *     · size  : 1 ~ 50 범위로 (너무 큰 값 요청 방지)
     *  2) 정렬 기준을 "createdAt DESC" 로 고정한다.
     *  3) keyword 가 있으면 검색 쿼리, 없으면 전체 조회 쿼리를 실행한다.
     *  4) Page&lt;Board&gt; 를 PageDTO 로 변환해 반환한다.
     *
     * [왜 DTO 변환을 Service 에서?]
     *  · OSIV false 환경에서는 컨트롤러/뷰가 Lazy 필드에 접근하면 예외가 난다.
     *  · 따라서 "트랜잭션이 살아있는 Service 안" 에서 엔티티 → DTO 변환을 끝낸다.
     */
    @Transactional(readOnly = true)
    public BoardResponse.PageDTO 게시글목록조회(int page, int size, String keyword) {
        // 1. 페이지/사이즈 보정
        int validPage = Math.max(0, page);
        int validSize = Math.max(1, Math.min(50, size));

        // 2. 정렬 및 Pageable 생성
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(validPage, validSize, sort);

        // 3. 검색어 유무에 따른 쿼리 분기
        Page<Board> boardPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            boardPage = boardRepository
                    .findByTitleContainingOrContentContaining(keyword.trim(), pageable);
        } else {
            boardPage = boardRepository.findAllWithUserOrderByCreatedAtDesc(pageable);
        }

        // 4. 엔티티 페이지를 DTO 로 변환 (트랜잭션 안에서)
        return new BoardResponse.PageDTO(boardPage);
    }

    // =========================================================================
    // 상세 조회
    // =========================================================================

    /**
     * 게시글 상세 조회 + 구매 여부 확인.
     *
     * [동작 흐름]
     *  1) JOIN FETCH 로 Board + User 한 방 조회
     *  2) 없으면 404
     *  3) 로그인 사용자가 있으면 "유료 글 구매 여부" 를 PurchaseService 에 물어본다
     *  4) DetailDTO 로 변환 (구매 여부 포함)
     */
    @Transactional(readOnly = true)
    public BoardResponse.DetailDTO 게시글상세조회(Long id, Long userId) {
        // 1. 게시글 + 작성자 로딩
        Board board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 2. 비회원이면 구매 여부 검사를 건너뛴다 (항상 false)
        boolean isPurchased = false;
        if (userId != null) {
            isPurchased = purchaseService.구매여부확인(userId, id);
        }

        // 3. DTO 변환
        return new BoardResponse.DetailDTO(board, isPurchased);
    }

    // =========================================================================
    // 작성
    // =========================================================================

    /**
     * 게시글 작성.
     *
     * [동작 흐름]
     *  1) DTO → Entity 변환 (작성자는 세션 User 를 직접 주입)
     *  2) INSERT
     */
    @Transactional
    public Board 게시글작성(BoardRequest.SaveDTO saveDTO, User user) {
        Board board = saveDTO.toEntity(user);
        return boardRepository.save(board);
    }

    // =========================================================================
    // 수정
    // =========================================================================

    /**
     * 게시글 수정 폼 데이터 조회.
     *
     * [동작 흐름]
     *  1) Board + User JOIN FETCH 로 조회
     *  2) 본인 여부 검사 (아니면 403)
     *  3) 수정 폼 DTO 로 변환
     */
    @Transactional(readOnly = true)
    public BoardResponse.UpdateFormDTO 게시글수정화면(Long id, Long userId) {
        Board board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));
        if (!board.isOwner(userId)) {
            throw new Exception403("게시글 수정 권한이 없습니다");
        }
        return new BoardResponse.UpdateFormDTO(board);
    }

    /**
     * 게시글 수정 처리.
     *
     * [동작 흐름]
     *  1) id 로 엔티티 조회 (404)
     *  2) 본인 여부 검사 (403)
     *  3) board.update(dto) 호출 → 더티 체킹으로 UPDATE
     *
     * [save() 호출 여부]
     *  @Transactional 안이고 영속 상태이므로 save() 는 필수 아니다.
     *  명시적 save() 호출은 "저장 동작을 분명하게 드러내기 위한 학습용 표기" 정도로 생각.
     */
    @Transactional
    public void 게시글수정(Long id, BoardRequest.UpdateDTO updateDTO, Long userId) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        if (!board.isOwner(userId)) {
            throw new Exception403("게시글 수정 권한이 없습니다");
        }

        try {
            board.update(updateDTO);
            boardRepository.save(board);
        } catch (Exception e) {
            throw new Exception500("게시글 수정 실패: " + e.getMessage());
        }
    }

    // =========================================================================
    // 삭제
    // =========================================================================

    /**
     * 게시글 삭제.
     *
     * [동작 흐름]
     *  1) 게시글 조회 (404)
     *  2) 본인 여부 검사 (403)
     *  3) 댓글 먼저 삭제 (reply_tb 의 FK 제약을 풀기 위해)
     *  4) 게시글 삭제
     *
     * [주의 - 왜 댓글부터 지우는가?]
     *  · reply_tb 의 board_id 컬럼이 board_tb.id 를 FK 로 참조하므로,
     *    게시글을 먼저 삭제하려고 하면 "자식 레코드가 있어 지울 수 없음" 오류가 난다.
     *  · 연관 엔티티에 Cascade 를 걸어도 되지만, 학습용으로는 흐름을 명시적으로 보여주는 쪽이 낫다.
     */
    @Transactional
    public void 게시글삭제(Long id, Long userId) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        if (!board.isOwner(userId)) {
            throw new Exception403("삭제 권한이 없습니다");
        }

        replyRepository.deleteByBoardId(id);
        boardRepository.deleteById(id);
    }
}
