package org.example.demo_ssr_v1_1.purchase;

import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception400;
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
 * Purchase (유료 게시글 구매) Service
 *
 * [이 Service 가 책임지는 것]
 *  1) 구매 가능한 상태인지(유료인지, 이미 샀는지, 내 글 아닌지, 포인트 충분한지) 모두 검사한다.
 *  2) "포인트 차감 + 구매 내역 저장" 을 한 트랜잭션으로 묶는다.
 *  3) 마이페이지의 구매 내역 목록을 DTO 로 돌려준다.
 *
 * [학습 포인트 - 왜 전부 한 트랜잭션이어야 하나?]
 *  · "포인트는 빠졌는데 구매 기록은 안 남아 있다" 같은 일이 생기면 대참사.
 *  · 한 메서드에 @Transactional 을 걸어두면, 중간에 예외가 터졌을 때 자동 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    /** 유료 게시글 고정 가격 (학습 단순화용 상수) */
    private static final Integer PREMIUM_BOARD_PRICE = 500;

    // =========================================================================
    // 구매 내역 조회
    // =========================================================================

    /**
     * 특정 사용자의 구매 내역을 최신순으로 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<PurchaseResponse.ListDTO> 구매내역조회(Long userId) {
        List<Purchase> purchaseList = purchaseRepository.findAllByUserIdWithBoard(userId);
        return purchaseList.stream()
                .map(PurchaseResponse.ListDTO::new)
                .toList();
    }

    // =========================================================================
    // 구매 처리
    // =========================================================================

    /**
     * 유료 게시글 구매 처리.
     *
     * [동작 흐름]
     *  1) 게시글 존재 확인 (없으면 404)
     *  2) "유료 게시글" 인지 확인 (아니면 400)
     *  3) 본인 글 구매 시도 방지 (403)
     *  4) 중복 구매 차단 (이미 샀으면 400)
     *  5) 구매자(User) 조회 (없으면 404)
     *  6) 포인트 차감 (부족하면 도메인 메서드 내부에서 400 발생)
     *  7) Purchase 저장 (구매 이력)
     *  8) user.save() 로 포인트 변경 반영 (더티 체킹이 있어서 필수는 아니지만 명시)
     *
     * [트랜잭션 보장]
     *  · 7번에서 UNIQUE 제약 위반 등으로 예외가 터지면
     *    6번에서 차감한 포인트도 함께 롤백된다 (= 원자성).
     */
    @Transactional
    public void 구매하기(Long boardId, Long userId) {
        // 1. 게시글 조회
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 2. 유료 게시글 여부 확인
        if (board.getPremium() == null || !board.getPremium()) {
            throw new Exception400("유료 게시글이 아닙니다");
        }

        // 3. 본인 글 구매 방지
        if (board.isOwner(userId)) {
            throw new Exception403("자신이 작성한 게시글은 구매할 수 없습니다");
        }

        // 4. 중복 구매 방지
        if (purchaseRepository.existsByUserIdAndBoardId(userId, boardId)) {
            throw new Exception400("이미 구매한 게시글입니다");
        }

        // 5. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 6. 포인트 차감 (부족하면 내부에서 Exception400)
        user.deductPoint(PREMIUM_BOARD_PRICE);

        // 7. 구매 내역 저장
        Purchase purchase = Purchase.builder()
                .user(user)
                .board(board)
                .price(PREMIUM_BOARD_PRICE)
                .build();
        purchaseRepository.save(purchase);

        // 8. 포인트 반영 (트랜잭션 커밋 시 자동 UPDATE 가 나가지만, 학습용으로 명시)
        userRepository.save(user);
    }

    // =========================================================================
    // 구매 여부 조회 (상세 화면에서 본문을 보여줄지 결정할 때 사용)
    // =========================================================================

    /**
     * 특정 사용자가 특정 게시글을 이미 구매했는지 여부.
     *
     * @param userId  비로그인 상태면 null 로 들어올 수 있다. 그 경우 false.
     * @param boardId 게시글 id
     */
    @Transactional(readOnly = true)
    public boolean 구매여부확인(Long userId, Long boardId) {
        if (userId == null) {
            return false;
        }
        return purchaseRepository.existsByUserIdAndBoardId(userId, boardId);
    }
}
