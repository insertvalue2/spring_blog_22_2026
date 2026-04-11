package org.example.demo_ssr_v1_1.board;

import lombok.Data;
import org.example.demo_ssr_v1_1._core.utils.MyDateUtil;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Board 도메인 "응답(Response) DTO" 묶음
 *
 * [왜 엔티티 대신 DTO 를 쓰는가?]
 *  1) OSIV false 환경에서는 트랜잭션이 끝나면 LAZY 필드 접근 시 예외가 난다.
 *     → Service 트랜잭션 안에서 필요한 필드를 모두 꺼내서 DTO 로 포장해 전달한다.
 *  2) 뷰에 불필요한 필드(비밀번호 등) 가 노출되는 것을 방지한다.
 *  3) 계층 간 결합을 끊어준다.
 */
public class BoardResponse {

    // =========================================================================
    // 목록용 DTO
    // =========================================================================
    @Data
    public static class ListDTO {
        private Long id;
        private String title;
        private String username;
        private String createdAt;

        public ListDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            // 이 DTO 는 JOIN FETCH 로 user 가 미리 로딩된 상태에서 만들어진다고 가정한다.
            if (board.getUser() != null) {
                this.username = board.getUser().getUsername();
            }
            if (board.getCreatedAt() != null) {
                this.createdAt = MyDateUtil.timestampFormat(board.getCreatedAt());
            }
        }
    }

    // =========================================================================
    // 상세용 DTO
    // =========================================================================
    @Data
    public static class DetailDTO {
        private Long id;
        private String title;
        private String content;
        private Long userId;
        private String username;
        private String createdAt;
        private Boolean premium;
        private Boolean isPurchased;

        public DetailDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            this.content = board.getContent();
            this.premium = (board.getPremium() != null) ? board.getPremium() : false;
            this.isPurchased = false; // 기본값, 구매 여부는 아래 생성자로 세팅
            if (board.getUser() != null) {
                this.userId = board.getUser().getId();
                this.username = board.getUser().getUsername();
            }
            if (board.getCreatedAt() != null) {
                this.createdAt = MyDateUtil.timestampFormat(board.getCreatedAt());
            }
        }

        /** 유료 글 구매 여부까지 받아서 만드는 생성자 */
        public DetailDTO(Board board, boolean isPurchased) {
            this(board);
            this.isPurchased = isPurchased;
        }
    }

    // =========================================================================
    // 수정 폼용 DTO
    // =========================================================================
    @Data
    public static class UpdateFormDTO {
        private Long id;
        private String title;
        private String content;
        private String username;
        private Boolean premium;

        public UpdateFormDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            this.content = board.getContent();
            this.premium = (board.getPremium() != null) ? board.getPremium() : false;
            if (board.getUser() != null) {
                this.username = board.getUser().getUsername();
            }
        }
    }

    // =========================================================================
    // 페이징 DTO
    // =========================================================================

    /**
     * Spring Data JPA 의 Page&lt;Board&gt; 를 뷰에 전달하기 위한 DTO.
     *
     * [왜 Page 를 그대로 안 쓰나?]
     *  · Page 는 Spring Data 라이브러리 타입이라 뷰가 직접 의존하면 결합이 커진다.
     *  · Mustache 에서 다루기 쉬운 형태(숫자, boolean, 리스트)로 평탄화한다.
     */
    @Data
    public static class PageDTO {
        private List<ListDTO> content;
        private int number;                 // 현재 페이지 번호 (0-based)
        private int size;                   // 페이지 크기
        private int totalPages;             // 전체 페이지 수
        private long totalElements;         // 전체 게시글 수
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
        private Integer previousPageNumber; // 1-based 이전 페이지 번호
        private Integer nextPageNumber;     // 1-based 다음 페이지 번호
        private List<PageLink> pageLinks;   // 페이지 번호 버튼 목록

        public PageDTO(Page<Board> page) {
            // 1. content 변환 (엔티티 → ListDTO)
            this.content = page.getContent().stream()
                    .map(ListDTO::new)
                    .toList();

            // 2. 페이지 메타데이터 복사
            this.number = page.getNumber();
            this.size = page.getSize();
            this.totalPages = page.getTotalPages();
            this.totalElements = page.getTotalElements();
            this.first = page.isFirst();
            this.last = page.isLast();
            this.hasNext = page.hasNext();
            this.hasPrevious = page.hasPrevious();

            // 3. 이전/다음 페이지 번호 계산 (1-based 변환)
            //    · Spring Data 의 page.getNumber() 는 0-based 이다.
            //    · 뷰에서는 1,2,3... 으로 보여주는 게 자연스러우므로 변환한다.
            //    · 0-based 로 이전 페이지 이동 시 URL 의 page 파라미터는 "현재번호 + 0" 이 되는데,
            //      1-based 로 환산하면 "현재번호(0 기반) + 1 - 1 = 현재번호" 라 그대로 들어간다.
            this.previousPageNumber = page.hasPrevious() ? page.getNumber() : null;
            this.nextPageNumber = page.hasNext() ? page.getNumber() + 2 : null;

            // 4. 페이지 버튼(숫자) 목록 생성
            this.pageLinks = generatePageLinks(page);
        }

        /**
         * 현재 페이지를 중심으로 앞뒤 2개씩 페이지 버튼을 만든다.
         *
         * [동작 흐름]
         *  1) 0-based → 1-based 로 변환한 현재 페이지 계산
         *  2) startPage = max(1, current - 2)
         *  3) endPage   = min(totalPages, current + 2)
         *  4) startPage ~ endPage 반복하며 PageLink 생성
         *  5) 현재 페이지는 active=true 로 표시
         */
        private List<PageLink> generatePageLinks(Page<Board> page) {
            List<PageLink> links = new ArrayList<>();
            int currentPage = page.getNumber() + 1;
            int totalPages = page.getTotalPages();

            int startPage = Math.max(1, currentPage - 2);
            int endPage = Math.min(totalPages, currentPage + 2);

            for (int i = startPage; i <= endPage; i++) {
                PageLink link = new PageLink();
                link.setDisplayNumber(i);
                link.setActive(i == currentPage);
                links.add(link);
            }
            return links;
        }
    }

    /** 페이지 번호 버튼 하나의 정보 */
    @Data
    public static class PageLink {
        private int displayNumber;
        private boolean active;
    }
}
