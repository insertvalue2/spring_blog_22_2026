package org.example.demo_ssr_v1_1.purchase;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;

public class PurchaseResponse {

    @Data
    public static class ListDTO {
        private Long id; // 구매내역 ID
        private Long boardId; // 게시글 ID
        private String boardTitle;
        private String boardAuthor;    // 게시글 작성자명
        private Integer price; // 구매 가격
        private String purchasedAt; // 구매일시 (포맷된 날짜)

        public ListDTO(Purchase purchase) {
            this.id = purchase.getId();
            this.price = purchase.getPrice();
            // 구매 일시 포맷팅
            if (purchase.getCreatedAt() != null) {
                this.purchasedAt = MyDateUtil.timestampFormat(purchase.getCreatedAt());
            }

            // 게시글 정보 (JOIN FETCH로 이미 로딩된 board 사용)
            if (purchase.getBoard() != null) {
                this.boardId = purchase.getBoard().getId();
                this.boardTitle = purchase.getBoard().getTitle();

                // 게시글 작성자 정보 (JOIN FETCH로 이미 로딩된 user 사용)
                if (purchase.getBoard().getUser() != null) {
                    this.boardAuthor = purchase.getBoard().getUser().getUsername();
                }
            }
        }
    }
}
