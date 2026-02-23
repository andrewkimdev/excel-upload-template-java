# Tariff Upload Plan (Revised)

## Summary
본 문서는 Tariff 업로드 기능 변경의 구현 지침을 결정 완료 상태로 고정한다.

- 글로벌 SPI/API breaking change 허용
- `createdBy`, `approvedYn`는 프론트에서 받지 않고 컨트롤러에서 강제 주입
- 공통값 계약은 `multipart(file + commonData JSON)`로 통일
- `commonData`는 엄격 검증(누락/타입오류/알 수 없는 필드 모두 400)
- 업로드 파일은 `.xlsx`만 허용 (`.xls` 정책/변환 로직은 범위 외)
- A/B 단일 트랜잭션 저장 + 실패 시 DB 전체 롤백
- A/B 키 정책은 현안 유지(A=`rowNumber` 포함, B=`equipCode` 중심)
- 동시성 업서트는 DB 유니크 제약 + 충돌 예외 재조회/재시도로 수렴
- 용어는 `tariffRate`로 통일

목표:
- 공통값 병합 처리
- Entity A/B 단일 트랜잭션 처리
- 감사 컬럼(`approvedYn`, `createdAt`, `createdBy`) 반영
- 업로드 보안 체인 유지

## Final Decisions
1. SPI/API breaking
- 데모 프로젝트 특성상 하위호환 계층 없이 즉시 변경한다.

2. `createdBy` 처리
- 요청으로 받지 않는다.
- 컨트롤러에서 `user01`로 강제 주입한다.
- 실프로젝트 전환 시 Spring Security principal 값으로 대체한다.

3. `approvedYn` 처리
- 요청으로 받지 않는다.
- 컨트롤러에서 `N`으로 강제 주입한다.

4. 키 전략 (현안 유지)
- Entity A: `comeYear + comeSequence + uploadSequence + equipCode + rowNumber`
- Entity B: `comeYear + comeSequence + uploadSequence + equipCode`
- 운영 규칙: A에서 `rowNumber`는 "업로드 파일 내 위치 식별자"로 간주한다.

5. 롤백 정책
- 배포 롤백 계획: 데모 범위에서 제외
- DB 트랜잭션 롤백: 필수 (A 성공 후 B 실패 시 전체 롤백)

6. 언어 정책
- 사용자 메시지/로그/코드 주석은 한국어로 유지한다.

7. 문서 참조 정책
- 제거된 `docs/SPEC-final.md`는 참조하지 않는다.
- 런타임 코드와 `README.md (as implemented)` 기준으로 정렬한다.

## Scope and Contract Changes
1. 오케스트레이터 변경
- `processUpload(file, templateType)` -> `processUpload(file, templateType, commonData)`

2. 저장 SPI 변경
- `saveAll(List<T> rows)` -> `saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`

3. REST 업로드 계약 (`POST /api/excel/upload/{templateType}`)
- `multipart/form-data`
- 파트:
  - `file`: Excel 파일 (`.xlsx`만 허용)
  - `commonData`: JSON (`application/json`, 필수)
- `commonData` required:
  - `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`
- 금지:
  - `createdBy` 클라이언트 입력
  - `approvedYn` 클라이언트 입력
- 검증 정책(엄격):
  - `commonData` 누락 -> 400
  - required 누락/빈값 -> 400
  - 타입 불일치 -> 400
  - 알 수 없는 필드 포함 -> 400

4. Thymeleaf 업로드 계약 (`POST /upload`)
- 폼 필드: `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`, `file(.xlsx)`
- `createdBy`, `approvedYn` 입력 필드는 제공하지 않는다.
- 서버에서 `createdBy=user01`, `approvedYn=N` 고정 주입

5. 신규 공통 DTO
- `UploadCommonData`
- 필드:
  - `comeYear` (필수)
  - `comeSequence` (필수)
  - `uploadSequence` (필수)
  - `equipCode` (필수)
- 서버 주입 필드(요청 DTO에 두지 않음):
  - `createdBy`
  - `approvedYn`

## Data and Persistence Strategy
1. 감사 컬럼
- Tariff A/B 엔티티에 `approvedYn`, `createdAt`, `createdBy` 추가
- 저장 시 기본값:
  - `approvedYn`: `N`
  - `createdAt`: 서버 현재 시각
  - `createdBy`: `user01`

2. 업서트 규칙
- A: 동일 식별키(업로드 단위 + `equipCode` + `rowNumber`)면 update, 아니면 insert
- B: 동일 식별키(업로드 단위 + `equipCode`)면 update, 아니면 insert
- `rowsUpdated` 의미를 위 식별키 기준으로 재정의

3. 동시성 업서트 전략
- DB 유니크 제약을 단일 진실원으로 사용한다.
- 저장 절차:
  - 1차 조회 후 update/insert 시도
  - unique constraint 충돌 발생 시 재조회
  - 재조회 결과 존재하면 update로 전환
- 재시도 횟수는 제한(예: 1~2회)하고, 초과 시 한국어 업무 오류로 반환한다.

4. 유니크 검증 정책 정렬
- A: 업로드 단위 + `equipCode` + `rowNumber`
- B: 업로드 단위 + `equipCode`

## Implementation Plan
1. 1단계: 계약/시그니처 브레이킹 반영
- 컨트롤러 바인딩: `file + commonData` 수용
- 엄격 `commonData` 검증 및 400 처리
- 오케스트레이터/`PersistenceHandler`/템플릿 핸들러 시그니처 일괄 반영

2. 2단계: `.xlsx` 전용 정책 확정
- 업로드 진입점에서 `.xlsx`만 허용
- `.xls` 허용/변환/가이드 문구 제거

3. 3단계: Tariff A/B 트랜잭션 저장 구현
- `@Transactional` 서비스에서 A -> B 저장
- 중간 예외 시 전체 롤백
- 감사 컬럼 주입 규칙 일관 적용

4. 4단계: 키 안정성 운영 규칙 반영
- A의 `rowNumber` 의미를 명시하고 결과 카운트 집계 기준 고정
- B의 `equipCode` 중심 upsert 기준 고정

5. 5단계: 동시성 업서트 반영
- DB 유니크 제약 정의/정렬
- 충돌 예외 핸들링(재조회/재시도) 구현

6. 6단계: UI/메시지 정비
- `upload.html`에 공통 필드 입력 추가
- 결과/에러 메시지 한국어 유지
- 내부 예외 상세 비노출 유지

7. 7단계: 테스트 전면 개편
- 기존 계약 가정 테스트를 새 계약 기준으로 재작성
- 컨트롤러/서비스/통합/회귀 테스트 갱신 후 `./gradlew test` 통과

## Test Plan
1. 컨트롤러 계약 테스트
- `commonData` 미전송 시 400
- required 공통값 누락/빈값 시 400
- 타입 오류 시 400
- 알 수 없는 필드 포함 시 400
- `.xls` 업로드 시 400
- 정상 `.xlsx + commonData` 입력 시 성공 응답 및 카운트 검증

2. 서버 주입값 테스트
- 저장된 `createdBy=user01` 검증
- 저장된 `approvedYn=N` 검증
- 저장된 `createdAt` 존재 검증

3. 서비스/통합 테스트
- A/B 동시 저장 성공
- B 저장 실패 유도 시 A/B 전체 롤백
- 내부 예외 발생 시 사용자 응답에 상세 예외 미노출 검증

4. 키/업서트 테스트
- A: 동일 업로드 단위 + 동일 `equipCode`에서 `rowNumber`가 다르면 insert
- A: 동일 식별키 재업로드 시 update
- B: 동일 식별키 재업로드 시 update

5. 동시성 테스트
- 동일 키 동시 업로드 경쟁 시 unique constraint 충돌 발생/복구 경로 검증
- 재시도 후 단일 상태로 수렴하는지 검증

6. 타입/파싱 테스트
- `tariffRate` `BigDecimal` 파싱/저장 성공
- 잘못된 숫자 형식 실패 처리

7. 보안 회귀 테스트
- filename sanitization 유지
- magic-byte validation 유지
- secure workbook opening 유지
- row pre-count / row-limit checks 유지

## Assumptions and Defaults
1. 데모 프로젝트 정책으로 breaking change 즉시 반영 가능
2. 공통값 계약 전환에 대한 단계적 하위호환은 생략
3. `createdBy`는 데모 기본값 `user01`로 고정
4. 실서비스에서는 `createdBy`를 Spring Security에서 추출하도록 대체
5. 배포 롤백 계획은 이번 범위에서 제외
6. DB 트랜잭션 롤백은 이번 범위에서 필수
7. 레거시 `.xls` 지원은 이번 범위에서 완전 제외
