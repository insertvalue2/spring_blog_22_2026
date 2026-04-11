package org.example.demo_ssr_v1_1.purchase;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

/**
 * Purchase 도메인 응답 DTO 묶음.
 *
 * [주의]
 *  · board.user 까지 접근하므로, 이 DTO 를 만들기 전에
 *    Repository 에서 "Purchase + Board + Board.user" 가 모두 로딩돼 있어야 한다.
 *  · PurchaseRepository.findAllByUserIdWithBoard() 가 그 역할을 한다.
 *  · 단, 현재는 Purchase.user 만 JOIN FETCH 하고 board.user 는 batch 로딩에 의존한다.
 */
public class PurchaseResponse {

    @Data
    public static class ListDTO {
        private Long id;
        private Long boardId;
        private String boardTitle;
        private String boardAuthor;
        private Integer price;
        private String purchasedAt;

        public ListDTO(Purchase purchase) {
            // 1. 기본 필드 복사
            this.id = purchase.getId();
            this.price = purchase.getPrice();

            // 2. 구매 일시 포맷팅
            if (purchase.getCreatedAt() != null) {
                this.purchasedAt = MyDateUtil.timestampFormat(purchase.getCreatedAt());
            }

            // 3. 구매한 게시글 정보
            if (purchase.getBoard() != null) {
                this.boardId = purchase.getBoard().getId();
                this.boardTitle = purchase.getBoard().getTitle();

                // 4. 게시글 작성자 이름
                if (purchase.getBoard().getUser() != null) {
                    this.boardAuthor = purchase.getBoard().getUser().getUsername();
                }
            }
        }
    }
}
