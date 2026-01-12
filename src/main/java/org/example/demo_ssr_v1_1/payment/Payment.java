package org.example.demo_ssr_v1_1.payment;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Table(name = "payment_tb")
@Entity
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 포트원 결제 고유 번호
    @Column(unique = true, nullable = false)
    private String impUid;

    // 가맹점 주문 번호 (중복 방지) - 우리 서버에 결제 고유 번호
    @Column(unique = true, nullable = false)
    private String merchantUid;

    // 결제한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 결제 금액 (포인트)
    @Column(nullable = false)
    private Integer amount;

    // 결제 상태 (paid: 결제 완료, cancelled: 취소됨)
    @Column(nullable = false)
    private String status;

    @CreationTimestamp
    private Timestamp createdAt;


    @Builder
    public Payment(String impUid, String merchantUid, User user, Integer amount, String status) {
        this.impUid = impUid;
        this.merchantUid = merchantUid;
        this.user = user;
        this.amount = amount;
        this.status = status;
    }

}