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
 * Purchase (구매 내역) 엔티티
 *
 * [용도]
 *  · 유료 게시글을 구매한 이력을 기록한다.
 *  · (User, Board) 쌍에 UNIQUE 제약을 걸어 "중복 구매" 를 DB 수준에서 차단한다.
 *
 * [관계 설계]
 *  · Purchase → User  (N:1) : 구매자
 *  · Purchase → Board (N:1) : 구매한 게시글
 *  · 양방향은 학습 단순화를 위해 쓰지 않는다.
 *
 * [price 필드가 별도로 있는 이유]
 *  · 게시글 가격이 나중에 바뀔 수 있으므로,
 *    "이 구매가 얼마에 일어났는지" 를 구매 시점에 스냅샷으로 저장한다.
 */
@Data
@NoArgsConstructor
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    /** 구매 당시 차감한 포인트 */
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
