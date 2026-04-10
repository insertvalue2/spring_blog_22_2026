package org.example.demo_ssr_v1_1.board;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.demo_ssr_v1_1.user.User;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

/**
 * Board 엔티티 (board_tb 테이블과 매핑)
 *
 * [학습 포인트]
 *
 * 1. @ManyToOne(fetch = LAZY)
 *    - Board : User = N : 1 관계.
 *    - LAZY 로 설정해 User 가 "실제 사용될 때" 쿼리가 나가도록 한다.
 *    - 사용되지 않는 User 를 매번 함께 가져오면 성능 낭비이기 때문이다.
 *
 * 2. @JoinColumn(name = "user_id")
 *    - board_tb 테이블에 "user_id" 라는 FK 컬럼을 만들고 user_tb.id 와 연결한다.
 *
 * 3. @Lob + private String content
 *    - MySQL 에서는 LONGTEXT 로 매핑되어 4GB 까지 저장할 수 있다.
 *    - Summernote(WYSIWYG) 로 작성한 HTML(+ base64 이미지)이 저장되므로 큰 컬럼이 필요하다.
 *    - 주의: CLOB 타입에는 LOWER() 같은 String 함수를 직접 쓸 수 없다 (Hibernate 제약).
 *
 * 4. @ColumnDefault("false")
 *    - DDL 생성 시 premium 컬럼의 기본값을 false 로 지정한다.
 *    - 자바 필드 초기값(= false)과 DB 기본값을 둘 다 두는 것이 안전하다.
 */
@Data
@NoArgsConstructor
@Table(name = "board_tb")
@Entity
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    /** 본문 (Summernote HTML + base64 이미지가 들어가므로 LONGTEXT 로 저장) */
    @Lob
    private String content;

    /** 작성자 (N:1) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** 유료 게시글 여부 (기본값 false) */
    @ColumnDefault("false")
    private Boolean premium = false;

    /** JPA 저장 시점에 DB 의 NOW() 로 자동 세팅 */
    @CreationTimestamp
    private Timestamp createdAt;

    @Builder
    public Board(String title, String content, User user, Boolean premium) {
        this.title = title;
        this.content = content;
        this.user = user;
        // premium 은 null 가능성을 막고 기본 false 로 보정
        this.premium = (premium != null) ? premium : false;
    }

    /**
     * 게시글 수정 - 도메인 메서드.
     *
     * [동작 흐름]
     *  1) DTO 자체 검증 (필수 입력값)
     *  2) 필드 덮어쓰기 (title, content, premium)
     *  3) 작성자(user) 는 "변경 불가" 이므로 건드리지 않는다
     *
     * [더티 체킹]
     *  · 이 메서드를 @Transactional Service 안에서 호출하면
     *    커밋 시 Hibernate 가 자동으로 UPDATE 쿼리를 날려준다.
     */
    public void update(BoardRequest.UpdateDTO updateDTO) {
        updateDTO.validate();
        this.title = updateDTO.getTitle();
        this.content = updateDTO.getContent();
        this.premium = (updateDTO.getPremium() != null) ? updateDTO.getPremium() : false;
    }

    /**
     * 작성자 본인 여부 확인.
     *
     * [왜 필요한가?]
     *  · 수정/삭제 요청 시 URL 의 id 를 조작해 다른 사람 글을 건드리는 IDOR 공격을 막기 위해
     *    Service 에서 항상 이 메서드로 "이 글의 주인이 지금 로그인한 사용자인가?" 를 검사한다.
     */
    public boolean isOwner(Long userId) {
        if (this.user == null || userId == null) {
            return false;
        }
        return this.user.getId().equals(userId);
    }
}
