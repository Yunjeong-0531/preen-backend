# StayBook 백엔드 1차 구현 요구사항 (Claude Code 전달용, 전체 feature)

이 문서는 설계 단계에서 확정한 구조와 규칙입니다. 아래 내용대로 구현해 주세요. 문서와 기존 코드가 다르면 임의로 고치지 말고 먼저 물어봐 주세요.

참조 문서: `docs/StayBook_ERD_1차구현_Mermaid.md` (테이블 31개, 컬럼, NULL 허용, 유니크 제약의 기준)

## 1. 작업 방식

1. 시작 전에 기존 프로젝트의 빌드 설정, base package, 이미 구현된 코드를 먼저 읽는다.
2. 언어·프레임워크 버전과 base package는 기존 설정을 그대로 따른다.
3. 코드를 쓰기 전에 변경할 파일 목록과 순서를 계획으로 먼저 보여준다.
4. 8장의 단계 순서로 진행하고, 단계가 끝날 때마다 빌드와 테스트를 돌려 결과를 보고한 뒤 다음 단계로 넘어간다.
5. 이미 구현된 엔드포인트는 경로와 요청·응답 형식을 유지한다. 이 문서의 API 경로 중 기존 코드와 다른 것은 기존 코드를 우선한다.

## 2. 범위

목표는 "회원가입 → 숙소 조회 → 예약 → 결제 → 취소/환불 → 리뷰" 한 사이클이 동작하는 것이다.

| feature | 테이블 |
|---|---|
| user | USERS, USER_PERSONAL_INFO |
| auth | USER_CREDENTIALS, USER_SOCIAL_ACCOUNTS, LOGIN_ATTEMPTS, VERIFICATION_CODES, PASSWORD_RESET_TOKENS |
| terms | TERMS, USER_TERM_AGREEMENTS |
| host | HOST_BUSINESSES |
| accommodation | REGIONS, ACCOMMODATION_CATEGORIES, ACCOMMODATIONS, ACCOMMODATION_IMAGES, AMENITIES, ACCOMMODATION_AMENITIES, ROOMS, ROOM_IMAGES, REFUND_POLICIES, REFUND_POLICY_RULES |
| inventory | ROOM_INVENTORIES, INVENTORY_HOLDS |
| reservation | RESERVATIONS, RESERVATION_PRICE_ITEMS, RESERVATION_STATUS_HISTORY |
| payment | PAYMENTS, PAYMENT_LOGS, REFUNDS |
| review | REVIEWS |
| favorite | FAVORITES |

1차 전제: 숙박(1박 이상)만 지원, 예약 1건 = 객실 1개, 할인 없음, 정산 없음.

## 3. 패키지 구조

단일 모듈, common 공유 + feature 분리 구조다.

```
{base package}
├── common
│   ├── security      # SecurityConfig, JwtProvider, AuthUser, 역할별 접근 규칙
│   ├── redis         # RedisConfig, 키 prefix 상수
│   ├── crypto        # 개인정보 암호화 컨버터, 해시 유틸
│   ├── exception     # BaseException, ErrorCode 인터페이스, GlobalExceptionHandler
│   ├── response      # ApiResponse<T>, 페이지 응답
│   ├── storage       # 이미지 업로드 (StorageClient 인터페이스 + 구현체)
│   └── config        # JPA Auditing, CORS, 스케줄러
├── user
├── auth              # + oauth/ (소셜 제공자 클라이언트, OAuthStateStore)
├── terms
├── host
├── accommodation
├── inventory
├── reservation
├── payment           # + pg/ (PgClient 인터페이스 + 구현체, 웹훅 컨트롤러)
├── review            # + spotify/ (검색 클라이언트, 앱 토큰 캐시)
└── favorite

resources/db/migration
├── V1__user_auth_terms.sql
├── V2__host_accommodation.sql
├── V3__inventory.sql
├── V4__reservation_payment.sql
└── V5__review_favorite.sql
```

모든 feature의 내부는 `controller / service / domain / repository / dto / exception`으로 통일한다.

## 4. 공통 규칙

**의존 방향 (화살표 반대 방향 import 금지)**

```
review → reservation → inventory → accommodation → host → user
payment → reservation
favorite → accommodation
auth, terms → user
```

- `common`은 어떤 feature도 import하지 않는다.
- 다른 feature의 리포지터리를 직접 쓰지 않고 그 feature의 서비스를 호출한다.
- 다른 feature의 엔티티는 `@ManyToOne`이 아니라 `Long userId` 같은 ID 필드로 참조한다.
- payment와 reservation의 연결은 다음 두 가지로만 한다.
  - 결제 성공: payment가 `reservationService.confirm(reservationId)`를 직접 호출한다.
  - 예약 취소: reservation이 `ReservationCancelledEvent`를 발행하고, payment가 커밋 이후 리스너로 받아 환불한다.

**URL과 역할**

| 경로 | 접근 |
|---|---|
| `/api/auth/**`, `/api/terms`, 숙소·리뷰 조회(GET), `/api/payments/webhook` | 토큰 없이 접근 |
| `/api/host/**` | HOST |
| `/api/admin/**` | ADMIN (1차에는 엔드포인트 없음, 규칙만 등록) |
| 그 외 | 로그인한 사용자 |

- 역할은 `UserRole { GUEST, HOST, ADMIN }`이고 로그인 엔드포인트는 공통이다.
- 호스트 API는 리소스가 본인 소유인지 서비스에서 반드시 검사한다.

**예외와 응답**
- ErrorCode는 feature별 enum으로 둔다 (`AuthErrorCode`, `ReservationErrorCode` 등).
- 모든 응답은 `ApiResponse<T>` 규격을 따른다.

**DB**
- 스키마 변경은 Flyway 마이그레이션으로만 한다. 엔티티 자동 DDL 생성에 의존하지 않는다.
- 금액은 `decimal`, 엔티티에서는 `BigDecimal`을 쓴다.

**개인정보**
- 이메일, 전화번호, 예약자 전화번호는 암호화 저장한다. 조회는 `email_hash`, `phone_hash`로 한다.
- 비밀번호, 인증코드, 재설정 토큰, 리프레시 토큰은 해시로만 저장한다.

## 5. feature별 요구사항

### 5.1 user

- `GET /api/users/me`, `PATCH /api/users/me`, `DELETE /api/users/me`
- 탈퇴는 `deleted_at`을 채우는 soft delete이고, 그 회원의 리프레시 토큰을 전부 지운다.
- 다른 feature용 서비스 메서드: 성인 여부 조회, 역할 변경.

### 5.2 auth

| 경로 | 기능 |
|---|---|
| `POST /api/auth/signup` | 이메일 가입 (약관 동의, 생년월일 포함) |
| `GET /api/auth/check-email` | 이메일 중복 확인 |
| `POST /api/auth/login` | 로그인 |
| `POST /api/auth/refresh` | 토큰 재발급 |
| `POST /api/auth/logout` | 로그아웃 |
| `/api/auth/social/{provider}` | 소셜 로그인 |
| `POST /api/auth/email/verify`, `email/resend` | 이메일 인증코드 확인, 재발송 |
| `POST /api/auth/password/forgot`, `password/reset` | 비밀번호 재설정 |

- 로그인 실패 시 `failed_login_count`를 올리고, 기준 횟수(설정값, 기본 5회)를 넘으면 `locked_until`(기본 15분)까지 잠근다.
- 없는 계정으로 시도한 기록도 `LOGIN_ATTEMPTS`에 `user_id` NULL로 남긴다.
- 인증코드는 `attempt_count`가 5회를 넘으면 무효화한다.
- 리프레시 토큰은 Redis에만 저장한다. 재발급 시 기존 키를 지우고 새로 발급한다.
- 소셜 로그인
  - 제공자의 액세스 토큰은 저장하지 않는다.
  - 최초 로그인이면 USERS, USER_PERSONAL_INFO, USER_SOCIAL_ACCOUNTS를 한 트랜잭션으로 만든다. USER_CREDENTIALS 행은 만들지 않는다.
  - 소셜 이메일이 기존 회원과 같아도 자동 병합하지 않고, 기존 계정 로그인 후 연결하라는 에러를 반환한다.
  - 약관 동의와 생년월일을 입력하기 전까지는 가입 미완료 상태로 둔다.
- 미성년자: 만 14세 미만은 가입 거부, 만 14세 이상은 가입 허용.

### 5.3 terms

- `GET /api/terms`: 현재 유효한 약관 목록.
- 필수 약관에 동의하지 않으면 가입을 거부한다. 동의 기록은 약관 버전별로 남긴다.
- 초기 약관 데이터는 마이그레이션으로 넣는다.

### 5.4 host

- `POST /api/host-businesses`: 로그인한 회원이 사업자 등록.
- 사업자등록번호 형식 검증을 통과하면 바로 `approval_status = APPROVED`로 저장하고 회원 역할을 HOST로 바꾼다.
- 역할이 바뀌면 새 액세스 토큰을 응답에 담아 준다.
- `GET /api/host/business`: 본인 사업자 정보 조회.

### 5.5 accommodation

| 경로 | 기능 |
|---|---|
| `GET /api/regions`, `GET /api/amenities`, `GET /api/categories` | 지역, 편의시설, 카테고리 목록 |
| `GET /api/accommodations` | 목록 검색 (지역 `regionId`, 카테고리 `categoryId`, 인원 `guestCount`, 페이지) |
| `GET /api/accommodations/{id}` | 상세 (이미지, 편의시설, 객실, 환불정책) |
| `GET /api/host/accommodations` | 내 숙소 목록 (공개 상태와 관계없이 모두, 객실 수·공개 가능 여부 포함) |
| `GET /api/host/accommodations/{id}` | 내 숙소 상세 (판매 중지 객실 포함) |
| `POST/PATCH /api/host/accommodations` | 숙소 등록·세부사항 수정 (공개 상태는 바꾸지 않음) |
| `PATCH /api/host/accommodations/{id}/listing-status` | 공개 상태 변경 (DRAFT·LISTED·HIDDEN) |
| `POST/PATCH /api/host/accommodations/{id}/rooms` | 객실 등록·수정 (판매 중지 포함) |
| `PUT /api/host/accommodations/{id}/refund-policy` | 환불정책 설정 |

- 공개 조회에는 `listing_status = LISTED`인 숙소만 나온다.
- LISTED로 바꾸려면 판매 중인(ACTIVE) 객실과 환불정책이 있어야 한다.
- LISTED인 숙소의 마지막 ACTIVE 객실은 INACTIVE로 바꿀 수 없다. 숙소를 먼저 숨겨야 한다. 공개 전환과 객실 판매 중지는 숙소 행을 잠그고 검사한다.
- 환불정책은 수정하지 않고 새 버전을 만들어 `active`를 옮긴다. 기존 예약이 과거 버전을 참조하기 때문이다.
- 지역, 편의시설, 카테고리 초기 데이터는 마이그레이션으로 넣는다.
- 숙소는 카테고리 하나에 속한다. 등록할 때 `categoryId`는 필수이고, 검색 조건은 함께 쓰면 모두 만족하는 숙소만 나온다.

### 5.6 inventory

| 경로 | 기능 |
|---|---|
| `GET /api/accommodations/{id}/availability?checkIn&checkOut` | 객실별 예약 가능 여부와 기간 합계 요금 |
| `GET /api/host/rooms/{roomId}/inventories?startDate&endDate` | 기간별 재고 조회 (수량, 예약·선점 수, 가격, 마감. 양 끝 포함, 최대 366일) |
| `PUT /api/host/rooms/{roomId}/inventories` | 기간별 수량, 가격, 판매 마감 설정 |

- 다른 feature용 서비스 메서드: `hold`, `confirm`, `release` (모두 예약 ID 기준).
- 선점은 조건부 UPDATE로 한다. 영향받은 행이 0이면 매진으로 처리한다.
  `UPDATE room_inventories SET held_count = held_count + 1 WHERE id = ? AND is_closed = false AND booked_count + held_count < total_count`
- 여러 날짜를 선점할 때는 날짜 오름차순으로 처리하고, 하루라도 실패하면 전체를 롤백한다.
- `confirm`은 `held_count - 1`, `booked_count + 1`, hold 상태 CONFIRMED. `release`는 hold 상태에 따라 해당 카운트를 되돌리고 RELEASED.
- 같은 예약에 대해 `confirm`, `release`를 두 번 호출해도 결과가 같아야 한다.
- 재고 자동 생성 스케줄러: 매일 한 번, 오늘부터 N일(설정값, 기본 90일) 뒤까지 없는 날짜의 행을 만든다. 가격은 `ROOMS.nightly_price`를 쓴다.

### 5.7 reservation

| 경로 | 기능 |
|---|---|
| `POST /api/reservations` | 예약 생성 (PENDING + 재고 선점) |
| `GET /api/reservations/me`, `GET /api/reservations/{id}` | 내 예약 목록, 상세 |
| `POST /api/reservations/{id}/cancel` | 예약 취소 |
| `GET /api/host/reservations` | 내 숙소의 예약 목록 |

- 생성 조건: 예약자가 성인, `check_out > check_in`, `check_in`이 오늘 이후, `guest_count <= max_capacity`.
- 생성 시 한 트랜잭션으로 예약 저장, 재고 선점, 요금 항목 저장을 한다. `hold_expires_at`은 생성 시각 + 10분(설정값).
- 금액: `total_price` = 요금 항목 합계.
  - ROOM 항목 = 숙박일별 `ROOM_INVENTORIES.price` 합
  - EXTRA_PERSON 항목 = `(guest_count - capacity) × extra_person_fee × 박수`, 0 이하면 항목 없음
- 생성 시 적용 중인 환불정책과 규칙을 `refund_policy_snapshot`에 JSON으로 복사한다.
- `reservation_no`는 추측하기 어려운 고유 문자열로 만든다.
- 상태 전이는 아래만 허용하고, 바뀔 때마다 `RESERVATION_STATUS_HISTORY`에 기록한다.

| 전이 | 계기 |
|---|---|
| PENDING → CONFIRMED | 결제 성공 (재고 confirm) |
| PENDING → CANCELLED | 선점 만료 또는 결제 전 취소 (재고 release) |
| CONFIRMED → CANCELLED | 사용자 취소 (재고 release, 환불 이벤트 발행) |
| CONFIRMED → COMPLETED | 체크아웃 날짜 경과 |

- 취소 환불액: 스냅샷 규칙에서 체크인까지 남은 일수에 맞는 `refund_rate`를 결제 금액에 곱한다. 나머지는 위약금이다.
- 선점 만료 스케줄러: 1분마다 `hold_expires_at`이 지난 PENDING 예약을 취소 처리한다.
- 완료 처리 스케줄러: 매일 한 번 체크아웃이 지난 CONFIRMED 예약을 COMPLETED로 바꾼다.

### 5.8 payment

| 경로 | 기능 |
|---|---|
| `POST /api/payments/prepare` | 결제 준비. `order_id` 발급, READY 저장 |
| `POST /api/payments/confirm` | PG 승인 요청, 성공 시 PAID 및 예약 확정 |
| `POST /api/payments/webhook` | PG 웹훅 수신 |

- PG 연동은 `PgClient` 인터페이스 뒤에 둔다. 실제 PG사 구현체와 테스트용 가짜 구현체를 분리한다.
- 승인 전에 요청 금액이 예약의 `total_price`와 같은지 서버에서 검증한다.
- 같은 `order_id`로 중복 요청이 와도 결제는 한 번만 처리한다.
- 웹훅은 서명을 검증하고, `provider_event_id`가 이미 있으면 무시한다. 모든 PG 응답은 `PAYMENT_LOGS`에 남긴다.
- 결제는 성공했는데 예약이 이미 만료로 취소됐다면 즉시 전액 환불한다.
- `ReservationCancelledEvent`를 받으면 REFUNDS 행을 만들고 PG 취소를 호출한다. 실패하면 상태를 FAILED로 남기고 예약 취소는 되돌리지 않는다.

### 5.9 review

| 경로 | 기능 |
|---|---|
| `GET /api/accommodations/{id}/reviews?sort&rating` | 숙소 리뷰 목록 (별점 + 음악). `sort`: LATEST(기본)·RATING_DESC·RATING_ASC, `rating`: 1~5면 그 별점만 |
| `GET /api/accommodations/{id}/tracks` | 숙소별 트랙 목록 (노출 리뷰에 붙은 곡, 중복 제거, 최근순, 최대 50곡) |
| `POST /api/reviews`, `PATCH/DELETE /api/reviews/{id}` | 작성, 수정(별점·곡 변경), 삭제 |
| `GET /api/spotify/tracks/search?q=` | 트랙 검색 (로그인 필요) |

- 리뷰는 **별점(1~5)과 음악(Spotify 트랙)만** 남긴다. 둘 다 필수다. 리뷰 글, 사진, 호스트 답글은 없다.
- 작성 조건: 본인 예약, 예약 상태 COMPLETED, 예약당 1건.
- 별점순 정렬에서 같은 별점끼리는 최근 작성순이다.
- Spotify
  - 리뷰에는 트랙 ID 문자열만 저장한다. 제목, 아티스트, 커버 같은 메타데이터는 DB에 저장하지 않는다.
  - 검색 API는 Spotify 응답을 그대로 가공해 전달만 한다.
  - 앱 액세스 토큰은 Redis에 캐시한다.
  - `preview_url`은 쓰지 않는다. 재생은 프론트의 공식 임베드 플레이어가 맡는다.

### 5.10 favorite

- `POST /api/favorites/{accommodationId}`, `DELETE /api/favorites/{accommodationId}`, `GET /api/favorites/me`
- 이미 찜한 숙소를 다시 찜하거나, 없는 찜을 지워도 에러 없이 성공으로 응답한다.

### 5.11 이미지 업로드 (common/storage)

- `POST /api/images`: 파일을 받아 URL을 반환한다. 숙소, 객실이 이 URL을 저장한다.
- 저장소는 `StorageClient` 인터페이스 뒤에 두고, 1차 구현체는 로컬 디스크로 한다.
- 이미지 형식과 크기 제한을 검사한다.

## 6. Redis 키

| 키 | 값 | TTL |
|---|---|---|
| `refresh:{userId}:{tokenId}` | 토큰 해시, user agent, IP | 14일 |
| `oauth:state:{state}` | 리다이렉트 정보 | 5분 |
| `spotify:app_token` | Spotify 앱 액세스 토큰 | 토큰 만료 시각까지 |

로그아웃은 해당 `refresh` 키 삭제, 비밀번호 변경과 탈퇴는 그 회원의 `refresh` 키 전부 삭제.

## 7. 스케줄러

| 위치 | 주기 | 작업 |
|---|---|---|
| inventory | 매일 1회 | N일 뒤까지 재고 행 생성 |
| reservation | 1분 | 선점 만료된 PENDING 예약 취소 |
| reservation | 매일 1회 | 체크아웃 지난 예약 COMPLETED 처리 |

## 8. 구현 단계

| 단계 | 내용 | 마이그레이션 |
|---|---|---|
| 1 | common (security, redis, crypto, exception, response, storage) | 없음 |
| 2 | user, auth, terms | V1 |
| 3 | host, accommodation | V2 |
| 4 | inventory | V3 |
| 5 | reservation, payment | V4 |
| 6 | review, favorite | V5 |
| 7 | 전체 시나리오 통합 테스트 | 없음 |
| 변경 | 숙소 카테고리 추가 (테이블, 목록 API, 검색 조건) | V6 |

각 단계에서 마이그레이션 → 엔티티·리포지터리 → 서비스 → 컨트롤러 → 테스트 순으로 작업한다.

## 9. 하지 말 것

- 멀티모듈 구조로 바꾸지 않는다.
- 참조 ERD에 없는 테이블을 만들지 않는다 (MFA, 기기 관리, 감사 로그, 정산, 쿠폰, 대실, 옵션, 문의, 알림은 2차).
- 리프레시 토큰용 DB 테이블, Spotify 트랙 테이블을 만들지 않는다.
- Redisson 분산락, Spring Batch, Master/Replica 라우팅을 도입하지 않는다.
- 새 라이브러리가 필요하면 추가하기 전에 이유와 함께 먼저 묻는다.
- 실제 PG 키, 소셜 로그인 키, Spotify 키를 코드나 저장소에 넣지 않는다. 환경 변수로 받는다.

## 10. 완료 기준

- 빈 DB에서 V1부터 V6까지 Flyway 마이그레이션이 오류 없이 적용된다.
- 빌드와 전체 테스트가 통과한다.
- 4장의 의존 방향을 어기는 import가 없다.
- 통합 테스트로 다음 시나리오가 확인된다.
  1. 가입 → 로그인 → 사업자 등록 → 숙소·객실 등록 → 재고 설정
  2. 다른 회원이 숙소 조회 → 예약 → 결제 → 예약 확정
  3. 예약 취소 → 환불정책에 따른 환불액 계산 → 재고 복구
  4. 결제하지 않은 예약이 만료되어 재고가 복구됨
  5. 재고가 1개인 날짜에 동시에 두 건을 예약하면 한 건만 성공함
  6. 체크아웃 후 리뷰 작성, 같은 예약으로 두 번째 리뷰는 거부됨
  7. 미성년 회원의 예약, GUEST의 `/api/host/**` 접근이 거부됨
