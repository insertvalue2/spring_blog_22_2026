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
 * 구매 서비스
 * 
 * 유료 게시글 구매 로직을 처리합니다.
 * - 포인트 차감
 * - 구매 내역 저장
 * - 중복 구매 방지
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    // 유료 게시글 기본 가격 (500포인트)
    private static final Integer PREMIUM_BOARD_PRICE = 500;

    @Transactional(readOnly = true)
    public List<PurchaseResponse.ListDTO> 구매내역조회(Long userId) {
        List<Purchase> purchaseList = purchaseRepository.findAllByUserIdWithBoard(userId);

        return purchaseList.stream()
                .map(PurchaseResponse.ListDTO::new)
                .toList();
    }

    /**
     * 유료 게시글 구매
     * 
     * 트랜잭션 처리:
     * - 포인트 차감과 구매 내역 저장을 하나의 트랜잭션으로 처리
     * - 실패 시 롤백하여 데이터 정합성 보장
     * 
     * @param boardId 게시글 ID
     * @param userId 구매자 ID
     * @throws Exception404 게시글이 없거나 유료 게시글이 아닌 경우
     * @throws Exception400 이미 구매한 게시글이거나 포인트가 부족한 경우
     * @throws Exception403 자신이 작성한 게시글을 구매하려는 경우
     */
    @Transactional
    public void 구매하기(Long boardId, Long userId) {
        // 1. 게시글 조회
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 2. 유료 게시글인지 확인
        if (board.getPremium() == null || !board.getPremium()) {
            throw new Exception400("유료 게시글이 아닙니다");
        }

        // 3. 작성자가 자신의 게시글을 구매하려는 경우 방지
        if (board.isOwner(userId)) {
            throw new Exception403("자신이 작성한 게시글은 구매할 수 없습니다");
        }

        // 4. 이미 구매한 게시글인지 확인
        if (purchaseRepository.existsByUserIdAndBoardId(userId, boardId)) {
            throw new Exception400("이미 구매한 게시글입니다");
        }

        // 5. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception404("사용자를 찾을 수 없습니다"));

        // 6. 포인트 확인 및 차감
        user.deductPoint(PREMIUM_BOARD_PRICE);

        // 7. 구매 내역 저장
        Purchase purchase = Purchase.builder()
                .user(user)
                .board(board)
                .price(PREMIUM_BOARD_PRICE)
                .build();
        
        purchaseRepository.save(purchase);
        
        // 8. 사용자 정보 저장 (포인트 차감 반영)
        userRepository.save(user);
    }

    /**
     * 구매 여부 확인
     * 
     * @param userId 사용자 ID
     * @param boardId 게시글 ID
     * @return 구매 여부
     */
    @Transactional(readOnly = true)
    public boolean 구매여부확인(Long userId, Long boardId) {
        if (userId == null) {
            return false;
        }
        return purchaseRepository.existsByUserIdAndBoardId(userId, boardId);
    }
}

