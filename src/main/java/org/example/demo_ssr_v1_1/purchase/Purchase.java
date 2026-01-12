package org.example.demo_ssr_v1_1.purchase;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1.board.Board;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;


/**
 * 구매 내역 엔티티

 * User와 Board의 Many-to-Many 관계를 Purchase란 중간 테이블로 구현합니다.
 * - 한 사용자는 여러 게시글을 구매할 수 있습니다.
 * - 한 게시글은 여러 사용자에게 구매될 수 있습니다.

 * 단방향 관계 설계:
 * - Purchase -> User (ManyToOne): 구매한 사용자
 * - Purchase -> Board (ManyToOne): 구매한 게시글
 */
@Data
@NoArgsConstructor
// 홍길동, 1번 게시글 구매 (유니크)
// 홍길동, 1번 게시글 구매 (중복 불가)
@Table(
        name = "purchase_tb",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_board", columnNames = {"user_id", "board_id"})
        }
)
@Entity
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 단방향 관계: Purchase -> User (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 단방향 관계: Purchase -> Board (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    // 구매 시 지불한 포인트
    private Integer price;

    @CreationTimestamp
    private Timestamp createdAt;

    @Builder
    public Purchase(User user, Board board, Integer price) {
        this.user = user;
        this.board = board;
        this.price = price;
    }

}
