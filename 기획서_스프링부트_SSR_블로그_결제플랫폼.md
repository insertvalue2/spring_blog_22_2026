# Spring Boot SSR Team Project

# 블로그·결제 플랫폼

## 기획서 (요구사항 · DB · 클래스 · 화면 명세)

기술: Java 17  |  Spring Boot 3.5  |  Spring Data JPA  |  Mustache  |  MySQL 8  |  Lombok

팀 인원: 4명  |  예상 소요 기간: 4주 (20일)

⚠️ 코딩 전에 이 문서를 먼저 읽으세요!

---

## 0. 프로젝트 개요

**한 줄 소개**: 회원이 글을 쓰고, 유료 콘텐츠를 만들고, 포인트로 결제하고, 필요하면 환불까지 받을 수 있는 SSR 기반 블로그 플랫폼.

**학습 목표 요약**: Spring MVC / JPA 영속성 / 트랜잭션 / 인증·인가 / 파일 업로드 / N+1 해결 / 외부 API 연동(PortOne, 카카오 OAuth, SMTP) / 보안 기초(해싱, XSS, CSRF, IDOR)

**구현 우선순위**: ★★★ 필수(Must) · ★★ 권장(Should) · ★ 선택(Could)

---

## 1. 요구사항 명세서

### 1-1. 필수 기능 — 회원 / 인증

| ID | 기능명 | 설명 | 우선순위 |
|---|---|---|---|
| F-01 | 회원가입 (이메일 인증) | 이메일 인증 코드 확인 후에만 가입 처리 | ★★★ |
| F-02 | 비밀번호 해싱 | BCrypt로 단방향 해싱 후 저장 | ★★★ |
| F-03 | 로컬 로그인 | username + password 인증, 세션에 저장 | ★★★ |
| F-04 | 로그아웃 | session.invalidate() 처리 | ★★★ |
| F-05 | 카카오 소셜 로그인 | OAuth 2.0 Authorization Code Grant | ★★ |
| F-06 | 회원정보 수정 | 본인만, 소셜 사용자는 제한 | ★★ |
| F-07 | 프로필 이미지 업로드 | MultipartFile, UUID 파일명, 이미지 검증 | ★★ |
| F-08 | 프로필 이미지 삭제 | 디스크 파일 + DB 필드 동시 정리 | ★ |

### 1-2. 필수 기능 — 게시판 / 댓글

| ID | 기능명 | 설명 | 우선순위 |
|---|---|---|---|
| F-09 | 게시글 목록 조회 (페이징) | Pageable, 생성일 DESC, size=5 기본 | ★★★ |
| F-10 | 게시글 상세 조회 | 작성자 username 포함, 구매 여부 판단 | ★★★ |
| F-11 | 게시글 작성 (Summernote) | 리치 에디터, @Lob 컬럼, 로그인 필수 | ★★★ |
| F-12 | 게시글 수정 | 본인 글만, 더티 체킹으로 UPDATE | ★★★ |
| F-13 | 게시글 삭제 | 본인 글만, 하드 삭제 | ★★★ |
| F-14 | 게시글 검색 | 제목 OR 본문 LIKE, 페이징과 호환 | ★★ |
| F-15 | 댓글 작성 | boardId + comment, 로그인 필수 | ★★★ |
| F-16 | 댓글 삭제 | 본인 댓글만 | ★★★ |

### 1-3. 필수 기능 — 포인트 / 결제 / 환불

| ID | 기능명 | 설명 | 우선순위 |
|---|---|---|---|
| F-17 | 유료 게시글 플래그 | Board.premium = true 시 본문 가림 | ★★★ |
| F-18 | 유료 게시글 구매 | 포인트 차감 + Purchase 저장 (트랜잭션) | ★★★ |
| F-19 | 중복 구매 방지 | 이미 구매 시 다시 구매 불가 | ★★★ |
| F-20 | 포인트 충전 (PortOne) | 결제 성공 후 서버 재검증 후 포인트 증가 | ★★★ |
| F-21 | 결제 내역 조회 | 본인 결제만, 시간 DESC | ★★ |
| F-22 | 구매 내역 조회 | 본인 구매 게시글 목록 | ★★ |
| F-23 | 환불 요청 | 본인 결제만, 사유 입력, 상태=PENDING | ★★ |
| F-24 | 환불 요청 목록 (내 요청) | 본인 요청만 표시 | ★★ |

### 1-4. 필수 기능 — 관리자 (ADMIN)

| ID | 기능명 | 설명 | 우선순위 |
|---|---|---|---|
| F-25 | 관리자 대시보드 | ADMIN 역할만 접근, AdminInterceptor로 보호 | ★★★ |
| F-26 | 환불 요청 목록 관리 | 전체 환불 요청 조회 | ★★ |
| F-27 | 환불 승인 처리 | PortOne 환불 API 호출 + 포인트 차감 + 상태 DONE | ★★ |
| F-28 | 환불 거절 처리 | 상태 REJECTED, 사유 기록 | ★★ |

### 1-5. 시스템 / 공통

| ID | 기능명 | 설명 | 우선순위 |
|---|---|---|---|
| F-29 | 공통 예외 처리 | @ControllerAdvice + 4xx/5xx 에러 페이지 | ★★★ |
| F-30 | 로그인 인터셉터 | 보호 URL 비로그인 시 /login 리다이렉트 | ★★★ |
| F-31 | 세션 인터셉터 | 모든 요청에 sessionUser 전달 | ★★ |
| F-32 | 역할 인터셉터 (Admin) | /admin/** 보호 | ★★★ |

### 1-6. 추후 개발 예정 기능 (Optional)

```
O-01  좋아요 기능        : Board ↔ User N:M, 중복 방지
O-02  태그(Tag) 기능     : 게시글 태그 등록·검색
O-03  알림 기능          : 내 글에 댓글 달리면 notification 생성
O-04  이미지 서버 업로드  : Summernote Base64 → 서버 저장 URL 전환
O-05  카테고리별 매출 통계 : GROUP BY category 집계 (관리자)
O-06  OAuth 추가         : 네이버 / 구글 소셜 로그인
O-07  스케줄링           : 환불 자동 리마인더 (Spring Scheduler)
O-08  CSRF 정식 도입      : Spring Security Filter Chain
```

---

## 2. 화면(URL) 설계서

### 2-1. 공개 화면 (비로그인 접근 가능)

| URL | 메서드 | 화면명 | 뷰(Mustache) | 비고 |
|---|---|---|---|---|
| `/`, `/board/list` | GET | 게시글 목록 | board/list | 페이징·검색 |
| `/board/{id}` | GET | 게시글 상세 | board/detail | 유료글은 본문 가림 |
| `/login` | GET | 로그인 폼 | user/login-form | |
| `/login` | POST | 로그인 처리 | (redirect) | 세션 저장 |
| `/join` | GET | 회원가입 폼 | user/join-form | 이메일 인증 포함 |
| `/join` | POST | 회원가입 처리 | (redirect) | 이메일 인증 확인 |
| `/user/kakao` | GET | 카카오 콜백 | (redirect) | code 파라미터 |

### 2-2. 회원 전용 (LoginInterceptor 보호)

| URL | 메서드 | 화면명 | 뷰 | 비고 |
|---|---|---|---|---|
| `/logout` | GET | 로그아웃 | (redirect) | |
| `/user/detail` | GET | 마이페이지 | user/detail | 본인 정보 |
| `/user/update` | GET/POST | 회원정보 수정 | user/update-form | 본인만 |
| `/user/charge-point` | GET/POST | 포인트 충전 | user/charge-point | PortOne JS |
| `/user/payment-list` | GET | 결제 내역 | user/payment-list | 본인 것 |
| `/user/purchase-list` | GET | 구매 내역 | user/purchase-list | 본인 것 |
| `/board/save` | GET/POST | 게시글 작성 | board/save-form | Summernote |
| `/board/{id}/update` | GET/POST | 게시글 수정 | board/update-form | 본인 글만 |
| `/board/{id}/delete` | POST | 게시글 삭제 | (redirect) | 본인 글만 |
| `/board/{id}/purchase` | POST | 유료글 구매 | (redirect) | 트랜잭션 |
| `/reply/save` | POST | 댓글 작성 | (redirect) | |
| `/reply/{id}/delete` | POST | 댓글 삭제 | (redirect) | 본인만 |
| `/refund/request/{paymentId}` | GET/POST | 환불 요청 | refund/request-form | 본인 결제만 |
| `/refund/list` | GET | 내 환불 목록 | refund/list | |

### 2-3. API (JSON)

| URL | 메서드 | 설명 | 요청 바디 | 응답 |
|---|---|---|---|---|
| `/api/email/send` | POST | 인증 코드 발송 | {email} | {message} |
| `/api/email/verify` | POST | 인증 코드 검증 | {email, code} | {message} |
| `/api/payment/verify` | POST | PortOne 결제 재검증 | {impUid, merchantUid} | {status} |

### 2-4. 관리자 전용 (AdminInterceptor 보호)

| URL | 메서드 | 화면명 | 뷰 | 비고 |
|---|---|---|---|---|
| `/admin/dashboard` | GET | 관리자 대시보드 | admin/dashboard | |
| `/admin/refund-list` | GET | 환불 요청 관리 | admin/admin-refund-list | 전체 요청 |
| `/admin/refund/{id}/approve` | POST | 환불 승인 | (redirect) | PortOne 호출 |
| `/admin/refund/{id}/reject` | POST | 환불 거절 | (redirect) | 사유 입력 |

### 2-5. 에러 화면

| URL | 상황 | 뷰 |
|---|---|---|
| — | Exception400 (잘못된 요청) | err/400 |
| — | Exception401 (미로그인) | /login 으로 redirect |
| — | Exception403 (권한 없음) | err/403 |
| — | Exception404 (없음) | err/404 |
| — | Exception500 (서버 오류) | err/500 |

---

## 3. DB 테이블 설계서

데이터베이스명: `myblog` (MySQL 8, utf8mb4)

### 3-1. user_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 로그인 ID (소셜은 닉네임_kakaoId) |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 이메일 (중복 방지) |
| profile_image | VARCHAR(500) | NULL 허용 | 파일명 또는 외부 URL |
| point | INT | DEFAULT 0 | 보유 포인트 |
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'LOCAL' | LOCAL / KAKAO |
| created_at | TIMESTAMP | DEFAULT NOW() | 가입일 |

### 3-2. user_role_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 소유 사용자 |
| role | VARCHAR(20) | NOT NULL | USER / ADMIN |

### 3-3. board_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| title | VARCHAR(200) | NOT NULL | 제목 |
| content | LONGTEXT | NOT NULL | 본문 (@Lob, Base64 이미지 포함) |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 작성자 |
| premium | BOOLEAN | DEFAULT FALSE | 유료 게시글 여부 |
| created_at | TIMESTAMP | DEFAULT NOW() | 작성일 |

### 3-4. reply_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| comment | VARCHAR(500) | NOT NULL | 댓글 내용 |
| board_id | BIGINT | NOT NULL, FK → board_tb.id | 대상 게시글 |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 작성자 |
| created_at | TIMESTAMP | DEFAULT NOW() | 작성 시각 |

### 3-5. payment_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| imp_uid | VARCHAR(100) | NOT NULL, UNIQUE | PortOne 결제 번호 |
| merchant_uid | VARCHAR(100) | NOT NULL, UNIQUE | 가맹점 주문 번호 |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 결제자 |
| amount | INT | NOT NULL | 결제 금액 (원) |
| status | VARCHAR(20) | NOT NULL | paid / cancelled |
| created_at | TIMESTAMP | DEFAULT NOW() | 결제 시각 |

### 3-6. purchase_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 구매자 |
| board_id | BIGINT | NOT NULL, FK → board_tb.id | 구매한 게시글 |
| price | INT | NOT NULL | 구매 당시 가격 (이력 보존) |
| created_at | TIMESTAMP | DEFAULT NOW() | 구매 시각 |

### 3-7. refund_request_tb 테이블

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고유 식별자 |
| payment_id | BIGINT | NOT NULL, FK → payment_tb.id | 대상 결제 |
| user_id | BIGINT | NOT NULL, FK → user_tb.id | 요청자 |
| reason | VARCHAR(500) | NOT NULL | 환불 사유 |
| status | VARCHAR(20) | NOT NULL | PENDING / APPROVED / REJECTED / DONE |
| admin_comment | VARCHAR(500) | NULL 허용 | 관리자 처리 메모 |
| created_at | TIMESTAMP | DEFAULT NOW() | 요청 시각 |
| processed_at | TIMESTAMP | NULL 허용 | 처리 완료 시각 |

### 3-8. 테이블 관계도

```
              user_tb 1 ─── N user_role_tb
                 │
                 ├─── 1:N ── board_tb ── 1:N ── reply_tb
                 │              │
                 │              └── 1:N ── purchase_tb
                 │                            │
                 ├─── 1:N ── payment_tb ─── 1:N ── refund_request_tb
                 │
                 └─── 1:N ── reply_tb
```

### 3-9. 핵심 설계 포인트

```
- user_tb.provider      : LOCAL / KAKAO 로 로그인 경로 구분
- board_tb.content      : @Lob(LONGTEXT), Summernote Base64 이미지 포함
- purchase_tb.price     : 구매 당시 가격을 저장해 가격 변동 이력 보존
- payment_tb.merchant_uid : 우리가 발급, 클라이언트 조작 방지의 기준점
- refund_request_tb.status : 상태 머신 (PENDING → APPROVED → DONE or REJECTED)
- password 는 반드시 BCrypt 해시로만 저장 (평문 금지)
- profile_image : http 로 시작하면 외부 URL(소셜), 아니면 파일명(로컬)
```

---

## 4. 클래스 설계서

### 4-1. 패키지 구조 (도메인 기준)

```
src/main/java/org/example/demo_ssr_v1_1
├── DemoSsrV11Application.java      ← 엔트리포인트
│
├── _core/                           ← 공통 인프라
│   ├── config/
│   │   └── WebMvcConfig             ← 인터셉터·리소스 핸들러·PasswordEncoder 빈
│   ├── errors/
│   │   ├── MyExceptionHandler       ← @ControllerAdvice (4xx/5xx 공통 처리)
│   │   └── exception/
│   │       ├── Exception400 / 401 / 403 / 404 / 500
│   ├── interceptor/
│   │   ├── SessionInterceptor       ← 모든 요청에 sessionUser 주입
│   │   ├── LoginInterceptor         ← 로그인 여부 검사
│   │   └── AdminInterceptor         ← ADMIN 권한 검사
│   └── utils/
│       ├── FileUtil                 ← 파일 저장·삭제·이미지 검증
│       ├── MailUtils                ← SMTP 전송 헬퍼
│       └── MyDateUtil               ← 날짜 포맷 유틸
│
├── user/                            ← 회원·인증·OAuth·메일
│   ├── User (Entity)
│   ├── UserRole (Entity)
│   ├── Role / OAuthProvider (Enum)
│   ├── UserRepository
│   ├── UserRequest / UserResponse (DTO)
│   ├── UserController               ← 로그인·회원가입·마이페이지
│   ├── UserApiController            ← 이메일 인증 API
│   ├── UserService
│   └── MailService
│
├── board/                           ← 게시글
│   ├── Board (Entity)
│   ├── BoardRepository
│   ├── BoardRequest / BoardResponse
│   ├── BoardController
│   └── BoardService
│
├── reply/                           ← 댓글
│   ├── Reply (Entity)
│   ├── ReplyRepository
│   ├── ReplyRequest / ReplyResponse
│   ├── ReplyController
│   └── ReplyService
│
├── payment/                         ← PortOne 결제
│   ├── Payment (Entity)
│   ├── PaymentRepository
│   ├── PaymentRequest / PaymentResponse
│   ├── PaymentController
│   └── PaymentService               ← PortOne REST 호출 + 검증
│
├── purchase/                        ← 유료 게시글 구매
│   ├── Purchase (Entity)
│   ├── PurchaseRepository
│   ├── PurchaseResponse
│   └── PurchaseService              ← 포인트 차감 + 구매 저장 (트랜잭션)
│
├── refund/                          ← 환불 요청·관리
│   ├── RefundRequest (Entity)
│   ├── RefundStatus (Enum)
│   ├── RefundRequestRepository
│   ├── RefundRequest / RefundResponse (DTO)
│   ├── RefundController
│   └── RefundService
│
└── admin/                           ← 관리자 화면
    └── AdminController
```

### 4-2. 호출 흐름

```
 Client(Browser)
        │
        ▼
 SessionInterceptor  →  LoginInterceptor  →  AdminInterceptor
        │
        ▼
  Controller (@Controller)
        │   - HTTP ↔ DTO 변환
        │   - 세션 읽기
        │   - Service 호출
        ▼
  Service (@Service, @Transactional)
        │   - 유효성·권한 검사
        │   - 여러 Repository 조합
        │   - 엔티티 → Response DTO 변환 (OSIV off)
        ▼
  Repository (JpaRepository)
        │   - findAll / findById / save
        │   - @Query (JPQL, JOIN FETCH)
        ▼
  DB (MySQL)
```

### 4-3. UserService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 회원가입(JoinDTO) | User | 이메일 중복 검사, BCrypt 해싱, 이미지 저장 |
| 로그인(LoginDTO) | User | findByUsernameWithRoles + matches |
| 회원정보수정화면(Long) | User | 본인 여부 검사 후 반환 |
| 회원정보수정(UpdateDTO, Long) | User | 더티 체킹, 로컬 사용자만 |
| 프로필이미지삭제(Long) | User | 파일 삭제 + 필드 null |
| 사용자이름조회(String) | User | 소셜 로그인용 |
| 소셜회원가입(User) | void | 신규 소셜 사용자 저장 |
| 카카오소셜로그인(String) | User | 토큰 발급 → 프로필 → 생성/조회 |
| 포인트충전(Long, Integer) | User | 포인트 증가 (테스트용) |

### 4-4. BoardService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 게시글목록조회(int, int, String) | PageDTO | Pageable + 검색어, 페이징 결과 DTO |
| 게시글상세조회(Long, Long) | DetailDTO | 구매 여부 포함 |
| 게시글작성(SaveDTO, User) | Board | 로그인 사용자 작성자 지정 |
| 게시글수정화면(Long, Long) | UpdateFormDTO | 본인 여부 검사 |
| 게시글수정(Long, UpdateDTO, Long) | void | 본인 글만, 더티 체킹 |
| 게시글삭제(Long, Long) | void | 본인 글만 |

### 4-5. ReplyService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 댓글작성(SaveDTO, Long) | Reply | boardId + userId + comment |
| 댓글삭제(Long, Long) | void | 본인 댓글만, IDOR 방지 |
| 댓글목록조회(Long, Long) | List&lt;ListDTO&gt; | 게시글별, 소유 여부 포함 |

### 4-6. PaymentService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 결제검증(String, String) | Payment | PortOne API로 재조회, 금액·상태 검증 |
| 결제내역조회(Long) | List&lt;ListDTO&gt; | 본인 결제만 |
| 결제조회(Long, Long) | Payment | 본인 결제 상세 |

### 4-7. PurchaseService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 구매하기(Long, Long) | void | 포인트 차감 + Purchase 저장 (트랜잭션) |
| 구매여부확인(Long, Long) | boolean | 중복 구매 방지 검사 |
| 구매내역조회(Long) | List&lt;ListDTO&gt; | 본인 구매 목록 |

### 4-8. RefundService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 환불요청화면검증(Long, Long) | Payment | 본인 결제 여부, 진행 중 여부 |
| 환불요청(SaveDTO, Long) | RefundRequest | 상태 PENDING으로 저장 |
| 내환불목록(Long) | List&lt;ListDTO&gt; | 본인 요청만 |
| 전체환불목록() | List&lt;ListDTO&gt; | 관리자용 |
| 환불승인(Long) | void | PortOne 환불 + 포인트 차감 + 상태 DONE |
| 환불거절(Long, String) | void | 상태 REJECTED + 사유 기록 |

### 4-9. MailService.java

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| 인증번호발송(String) | void | 6자리 코드 생성 → SMTP 전송 → 세션 저장 |
| 인증번호확인(String, String) | boolean | 세션 저장값과 비교 |

---

## 5. 예외 처리 체계

| 예외 | HTTP | 발생 상황 | 응답 처리 |
|---|---|---|---|
| Exception400 | 400 | 유효성 오류, 비즈니스 실패 | alert + history.back() |
| Exception401 | 401 | 미로그인 접근 | alert + /login 이동 |
| Exception403 | 403 | 권한 없음 (본인 X, ADMIN X) | err/403 페이지 |
| Exception404 | 404 | 리소스 없음 | err/404 페이지 |
| Exception500 | 500 | 서버 내부 오류 | err/500 페이지 |

모든 예외는 `MyExceptionHandler`(`@ControllerAdvice`)에서 중앙 처리.

---

## 6. 외부 연동 명세

### 6-1. PortOne (아임포트) 결제

| 항목 | 내용 |
|---|---|
| 용도 | 포인트 충전 결제 + 환불 API 호출 |
| 토큰 API | `POST https://api.iamport.kr/users/getToken` |
| 결제 조회 | `GET https://api.iamport.kr/payments/{imp_uid}` |
| 환불 API | `POST https://api.iamport.kr/payments/cancel` |
| 인증 키 | `${IMP_REST_API_KEY}`, `${IMP_SECRET_KEY}` (환경변수 필수) |
| 검증 원칙 | 클라이언트가 보낸 금액 믿지 않음. 서버가 PortOne에 재조회해 amount·status 검증 |

### 6-2. 카카오 OAuth 2.0

| 항목 | 내용 |
|---|---|
| 인가 요청 | `https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code` |
| 토큰 발급 | `POST https://kauth.kakao.com/oauth/token` |
| 프로필 조회 | `POST https://kapi.kakao.com/v2/user/me` |
| client_id | `${KAKAO_CLIENT_ID}` (환경변수) |
| client_secret | `${KAKAO_CLIENT_SECRET}` (환경변수) |
| redirect_uri | `http://localhost:8080/user/kakao` |

### 6-3. Gmail SMTP (이메일 인증)

| 항목 | 내용 |
|---|---|
| 용도 | 회원가입 이메일 인증 코드 발송 |
| 호스트 | smtp.gmail.com, 포트 587, STARTTLS |
| 계정 | `${MAIL_USERNAME}` |
| 비밀번호 | `${MAIL_PASSWORD}` (Gmail 앱 비밀번호) |
| 주의 | 일반 로그인 비밀번호 사용 금지, 반드시 앱 비밀번호 발급 |

---

## 7. 핵심 비즈니스 규칙

### 7-1. 인증·인가

```
R-01 비밀번호는 반드시 BCrypt 해시로만 저장한다.
R-02 로그인 성공 직후 세션 ID를 재발급한다 (Session Fixation 방어).
R-03 본인만 수정·삭제·환불 요청이 가능하다 (Service 계층에서 isOwner 검사).
R-04 관리자 전용 URL은 반드시 AdminInterceptor + Service 이중 검사.
R-05 회원가입은 이메일 인증 플래그가 세션에 있을 때만 성공한다.
R-06 이메일 인증 플래그는 일회용 (가입 성공 시 즉시 제거).
```

### 7-2. 결제·환불

```
R-07 결제 금액은 절대 클라이언트 값을 믿지 않는다.
     반드시 PortOne API로 재조회 후 우리 DB 금액과 대조한다.
R-08 유료 게시글 구매는 포인트 차감 + Purchase 저장을 하나의 트랜잭션으로 처리한다.
R-09 이미 구매한 게시글은 다시 구매할 수 없다.
R-10 이미 진행 중인 환불 요청이 있는 결제는 재요청 불가.
R-11 환불 승인 시: PortOne 환불 API 성공 → 사용자 포인트 차감 → 상태 DONE
     어느 하나라도 실패하면 전체 롤백.
R-12 RefundStatus 전이: PENDING → APPROVED → DONE  (or)  PENDING → REJECTED
```

### 7-3. 게시글·댓글

```
R-13 유료 게시글은 구매자와 작성자만 본문을 볼 수 있다.
R-14 본인이 쓴 게시글·댓글만 수정·삭제할 수 있다.
R-15 Summernote 본문은 HTML로 저장되므로 저장 시 반드시 살균(Jsoup Safelist) 한다.
R-16 삭제된 게시글의 댓글은 함께 제거된다 (cascade 또는 명시적 삭제).
```

### 7-4. 파일 업로드

```
R-17 업로드 파일명은 UUID로 재생성한다 (원본명 사용 금지).
R-18 확장자 화이트리스트: jpg / jpeg / png / gif / webp 만 허용.
R-19 파일 시그니처(매직 넘버)까지 검사한다.
R-20 SVG는 허용하지 않는다 (내부 스크립트 위험).
R-21 업로드 디렉터리 경로는 정규화 후 상위 경로 탈출을 차단한다.
```

---

## 8. 화면 플로우 (대표 시나리오)

### 8-1. 회원가입 (이메일 인증)

```
[회원가입 폼]
   ↓ 이메일 입력 + [인증번호 전송]
[POST /api/email/send]
   → MailService.인증번호발송 → SMTP 발송
   ↓ 인증번호 입력 + [확인]
[POST /api/email/verify]
   → 세션에 "email_verified_<email>" = true 저장
   ↓ 회원가입 버튼 활성화
[POST /join]
   → 세션 플래그 검증 → BCrypt 해싱 → User 저장 → 플래그 제거
   → redirect /login
```

### 8-2. 유료 게시글 구매

```
[GET /board/{id}]
   → premium=true, 미구매 → 본문 가림 + [구매하기 모달]
[POST /board/{id}/purchase]
   → PurchaseService.구매하기(boardId, userId)
     트랜잭션 시작
       1) User.deductPoint(500)     (부족하면 Exception400)
       2) Purchase 저장 (중복이면 Exception400)
     트랜잭션 커밋
   → redirect /board/{id}
[GET /board/{id}]
   → 구매 여부 확인 → 본문 노출
```

### 8-3. 포인트 충전 (PortOne)

```
[GET /user/charge-point]
   → PortOne JS SDK 로드, merchantUid 발급
   ↓ 사용자 결제
[PortOne 팝업 → 결제 성공 (impUid 수신)]
   ↓ AJAX
[POST /api/payment/verify]
   → PaymentService.결제검증(impUid, merchantUid)
     1) PortOne REST API로 결제 정보 재조회
     2) amount·status 검증
     3) Payment 저장
     4) User.chargePoint(amount) → 더티 체킹으로 포인트 반영
   → 완료 응답
```

### 8-4. 환불 (승인 플로우)

```
[사용자]    POST /refund/request/{paymentId} + 사유
            → RefundRequest(status=PENDING) 저장
[관리자]    GET /admin/refund-list
            → 전체 PENDING 목록 확인
            POST /admin/refund/{id}/approve
            → RefundService.환불승인(id)
              트랜잭션 시작
                1) PortOne cancel API 호출
                2) User.deductPoint(amount)  (충전된 포인트 회수)
                3) RefundRequest.status = DONE, processed_at = NOW()
                4) Payment.status = "cancelled"
              트랜잭션 커밋
```

---

## 9. 개발 일정 (4주 기준)

| 주차 | 마일스톤 | 완료 기준 (DoD) |
|---|---|---|
| 1주차 | 환경 세팅 + 게시판 CRUD (익명) | F-09~F-13 동작, Mustache 목록/상세/폼 |
| 2주차 | 회원·인증 + 권한·댓글 | F-01~F-08, F-15~F-16, F-25, 인터셉터·예외 체계 완성 |
| 3주차 | 파일 업로드 + 검색 + Summernote + 이메일 인증 | F-07, F-14, F-11, F-01 이메일 인증 연동 |
| 4주차 | 결제·구매·환불 + 관리자 + 카카오 로그인 + 보안 점검 | F-17~F-28, F-05, 보안 체크리스트 통과 |

---

## 10. 검수(DoD) 체크리스트

### 10-1. 기능 체크

```
□ 이메일 인증 없이 /join 에 직접 POST 해도 실패한다
□ 비밀번호가 DB에 BCrypt 해시($2a$...)로 저장돼 있다
□ 비로그인 상태에서 /board/save 접근 시 /login 으로 튕긴다
□ USER 역할로 /admin/** 접근 시 403
□ 내 글이 아닌 글의 /update, /delete URL을 수동으로 쳐도 403
□ 내 댓글이 아닌 댓글의 /delete URL을 수동으로 쳐도 403
□ 페이지 이동 시 검색어가 유지된다
□ Summernote로 이미지를 넣어 저장하면 상세에서 그대로 보인다
□ 유료 게시글은 구매 전엔 본문이 가려진다
□ 포인트 부족 시 구매 실패 메시지
□ 같은 유료 게시글을 두 번 구매할 수 없다
□ 클라이언트에서 결제 금액을 조작해도 서버가 잡는다
□ 내 결제가 아닌 결제에 환불 요청 시도 시 403
□ 이미 진행 중인 환불이 있는 결제에 재요청 불가
□ 관리자가 환불을 승인하면 포인트가 차감되고 상태가 DONE
□ 카카오 로그인 → 신규 가입 및 재로그인 모두 성공
```

### 10-2. 보안 체크

```
□ application-*.yml 에 메일 비밀번호, OAuth 키, PortOne 키가 하드코딩돼 있지 않다
□ Git 히스토리에 비밀 값이 들어가 있지 않다 (없다면 즉시 revoke)
□ Summernote 본문이 서버에서 살균(Jsoup) 된다
□ 쿠키에 HttpOnly / SameSite=Lax 가 설정돼 있다
□ 로그인 직후 세션 ID가 재발급된다
□ 파일 업로드가 확장자 화이트리스트 + 매직 넘버 검증을 통과해야 한다
□ 운영 프로파일(prod)에서 H2 콘솔이 꺼져 있다
□ System.out.println 이 모두 SLF4J log.xxx() 로 교체돼 있다
□ 민감 정보(토큰, 비밀번호, 카드번호)가 로그에 찍히지 않는다
```

### 10-3. 품질 체크

```
□ Controller 에서 JPA 엔티티를 직접 반환하지 않는다 (Request/Response DTO 사용)
□ Service 에 @Transactional 이 붙어 있다
□ Repository 쿼리는 @Query + @Param 으로 작성 (문자열 결합 금지)
□ 주요 상세 쿼리는 JOIN FETCH 또는 default_batch_fetch_size 로 N+1 방지
□ 각 기능마다 최소 1개 이상의 성공·실패 시나리오를 직접 테스트했다
```

---

## 11. 팀 역할 분담 (예시, 4명 기준)

| 역할 | 담당 도메인 | 주요 산출물 |
|---|---|---|
| 멤버 A | 인프라 · 공통 | `_core/*`, `WebMvcConfig`, 예외 체계, 인터셉터, 레이아웃 템플릿 |
| 멤버 B | 회원 · 인증 | `user/*`, `MailService`, 카카오 OAuth, 이메일 인증 |
| 멤버 C | 게시판 · 댓글 · 파일 | `board/*`, `reply/*`, `FileUtil`, Summernote, 검색 |
| 멤버 D | 결제 · 구매 · 환불 | `payment/*`, `purchase/*`, `refund/*`, 관리자 화면 |

> 단, 모든 멤버는 **다른 도메인의 Pull Request 1개 이상 리뷰**를 의무로 한다 (크로스 리뷰).

---

## 12. 팀 그라운드 룰

```
G-01 커밋 메시지: feat/fix/refactor/docs/chore + 한 줄 요약 (한글 OK)
G-02 브랜치 전략: main ← develop ← feature/<도메인-기능>
G-03 PR 머지 조건: 본인 push 금지, 다른 멤버 1명 이상 approve
G-04 하루 1회 15분 스탠드업: 어제 한 것 / 오늘 할 것 / 막힌 것
G-05 ".env"는 절대 commit 하지 않는다. .gitignore 에 반드시 추가.
G-06 모든 Service 메서드는 @Transactional 여부를 명시한다 (읽기 전용이면 readOnly=true).
G-07 프론트 검증은 UX 용이고, 진짜 검증은 서버에서 한다 (이중 방어).
```

---

## 13. 참고 자료

```
- 공식: https://spring.io/projects/spring-boot
- 공식: https://docs.spring.io/spring-data/jpa/reference/
- Summernote: https://summernote.org/
- PortOne(아임포트): https://portone.io/korea/ko
- 카카오 디벨로퍼스: https://developers.kakao.com/
- OWASP Top 10: https://owasp.org/www-project-top-ten/
```

---

**문서 버전**: v1.0  |  **최종 수정**: 2026-04-11

> 이 문서는 PM·기획자가 개발팀에 전달하는 **구현 사양서**입니다.
> 학생(개발팀)은 **코딩 전에 반드시 이 문서를 끝까지 읽고**, 팀 회의에서 모르는 용어를 0으로 만든 뒤 작업을 시작하세요.
