# Tariff Upload Plan (Revised)

## Summary
본 문서는 Tariff 업로드 기능 변경의 구현 지침을 결정 완료 상태로 고정한다.

- 글로벌 SPI/API breaking change 허용
- 단일 `UploadCommonData` 고정 계약을 제거하고 템플릿별 제네릭 `commonData` 계약으로 전환
- `createdBy`, `approvedYn`는 프론트에서 받지 않고 서버에서 강제 주입
- 공통값 계약은 `multipart(file + commonData JSON)` 유지, 단 `commonData` 스키마는 `templateType`별로 분기
- `commonData`는 엄격 검증(누락/타입오류/알 수 없는 필드 모두 400)
- 업로드 파일은 `.xlsx`만 허용 (`.xls` 정책/변환 로직은 범위 외)
- A/B 단일 트랜잭션 저장 + 실패 시 DB 전체 롤백
- A/B 키 정책은 현안 유지(A=`rowNumber` 포함, B=`equipCode` 중심)
- 동시성 업서트는 DB 유니크 제약 + 충돌 예외 재조회/재시도로 수렴
- 용어는 `tariffRate`로 통일

목표:
- 템플릿별 공통값 병합 처리
- Entity A/B 단일 트랜잭션 처리
- 감사 컬럼(`approvedYn`, `createdAt`, `createdBy`) 반영
- 업로드 보안 체인 유지

## Final Decisions
1. SPI/API breaking
- 데모 프로젝트 특성상 하위호환 계층 없이 즉시 변경한다.

2. `commonData` 타입 정책
- `commonData`는 템플릿별 DTO 타입을 사용한다.
- 타입 선택은 `templateType`으로 결정한다.
- 컨트롤러에서 템플릿 타입에 맞는 DTO로 엄격 역직렬화 + Bean Validation을 수행한다.

3. `createdBy` 처리
- 요청으로 받지 않는다.
- 서버에서 `user01`로 강제 주입한다.
- 실프로젝트 전환 시 Spring Security principal 값으로 대체한다.

4. `approvedYn` 처리
- 요청으로 받지 않는다.
- 서버에서 `N`으로 강제 주입한다.

5. 키 전략 (현안 유지)
- Entity A: `comeYear + comeSequence + uploadSequence + equipCode + rowNumber`
- Entity B: `comeYear + comeSequence + uploadSequence + equipCode`
- 운영 규칙: A에서 `rowNumber`는 "업로드 파일 내 위치 식별자"로 간주한다.

6. 롤백 정책
- 배포 롤백 계획: 데모 범위에서 제외
- DB 트랜잭션 롤백: 필수 (A 성공 후 B 실패 시 전체 롤백)

7. 언어 정책
- 사용자 메시지/로그/코드 주석은 한국어로 유지한다.

8. 문서 참조 정책
- 제거된 `docs/SPEC-final.md`는 참조하지 않는다.
- 런타임 코드와 `README.md (as implemented)` 기준으로 정렬한다.

## Scope and Contract Changes
1. 오케스트레이터 변경
- 외부 호출 계약: `processUpload(file, templateType, commonData)` 유지
- 내부 타입 계약: `TemplateDefinition<T, C>` 기준으로 `commonData`를 `C`로 처리

2. 저장 SPI 변경
- `saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`
- -> `saveAll(List<T> rows, List<Integer> sourceRowNumbers, C commonData)`

3. 템플릿 정의 변경
- `TemplateDefinition<T>` -> `TemplateDefinition<T, C>`
- 템플릿 정의에 `commonDataClass`를 포함하여 컨트롤러/오케스트레이터가 타입을 결정할 수 있게 한다.

4. REST 업로드 계약 (`POST /api/excel/upload/{templateType}`)
- `multipart/form-data`
- 파트:
  - `file`: Excel 파일 (`.xlsx`만 허용)
  - `commonData`: JSON (`application/json`, 필수)
- `commonData` 스키마: `templateType`별 DTO 스키마를 따른다.
- 검증 정책(엄격):
  - `commonData` 누락 -> 400
  - required 누락/빈값 -> 400
  - 타입 불일치 -> 400
  - 알 수 없는 필드 포함 -> 400 (`FAIL_ON_UNKNOWN_PROPERTIES`)

5. Thymeleaf 업로드 계약 (`POST /upload`)
- 현 단계는 tariff 템플릿 입력(`comeYear`, `comeSequence`, `uploadSequence`, `equipCode`)을 유지한다.
- `createdBy`, `approvedYn` 입력 필드는 제공하지 않는다.
- 서버에서 `createdBy=user01`, `approvedYn=N` 고정 주입
- 향후 다중 템플릿 UI 필요 시 템플릿별 동적 폼 설계로 확장한다.

## Common Data Type Strategy
1. 전역 단일 DTO 폐기
- 단일 전역 `UploadCommonData`를 코어 계약으로 고정하지 않는다.

2. 템플릿별 DTO 도입
- 각 템플릿은 자체 `XxxCommonData` DTO를 가진다.
- Tariff 템플릿은 `TariffExemptionCommonData`를 사용한다.

3. 서버 주입 필드 정책
- `createdBy`, `approvedYn`는 요청 DTO에 의존하지 않고 서버에서 강제 적용한다.

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
1. 1단계: 코어 SPI 제네릭화
- `PersistenceHandler<T, C>`로 변경
- `TemplateDefinition<T, C>` + `commonDataClass` 추가
- 오케스트레이터 내부 타입 흐름을 `T/C`로 정렬

2. 2단계: 컨트롤러 템플릿별 strict `commonData` 파싱
- `templateType`으로 템플릿 정의 조회
- `commonDataClass` 기반 역직렬화
- `FAIL_ON_UNKNOWN_PROPERTIES` 및 scalar coercion 차단 유지
- Bean Validation 실패 시 400 반환

3. 3단계: Tariff 모듈 마이그레이션
- Tariff 서비스 시그니처를 `PersistenceHandler<TariffExemptionDto, TariffExemptionCommonData>`로 전환
- Tariff 템플릿 정의에 `TariffExemptionCommonData.class` 바인딩
- A/B 트랜잭션 저장 및 감사 컬럼 주입 규칙 유지

4. 4단계: 테스트 계약 재정렬
- `UploadCommonData` 고정 타입 계약 테스트 제거
- 템플릿별 `commonDataClass` 계약 테스트 추가
- REST strict 검증/보안 회귀 테스트 유지

5. 5단계: 문서/가이드 정렬
- README의 새 템플릿 추가 가이드를 `PersistenceHandler<T, C>` 기준으로 갱신
- Tariff 예시를 템플릿별 `commonData` 관점으로 보정

## Test Plan
1. 컨트롤러 계약 테스트
- `commonData` 미전송 시 400
- 템플릿별 required 공통값 누락/빈값 시 400
- 템플릿별 타입 오류 시 400
- 템플릿별 알 수 없는 필드 포함 시 400
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

7. 계약/리플렉션 테스트
- `PersistenceHandler<T, C>` 시그니처 확인
- `TemplateDefinition<T, C>`와 `commonDataClass` 보유 확인
- 오케스트레이터가 템플릿 정의 기준으로 `commonData`를 전달하는지 확인

8. 보안 회귀 테스트
- filename sanitization 유지
- magic-byte validation 유지
- secure workbook opening 유지
- row pre-count / row-limit checks 유지

## Assumptions and Defaults
1. 데모 프로젝트 정책으로 breaking change 즉시 반영 가능
2. 공통값 계약 전환에 대한 단계적 하위호환은 생략
3. `commonData`는 템플릿별 DTO 스키마를 사용
4. unknown field 거부 등 strict 파싱 정책은 모든 템플릿에서 유지
5. `createdBy`는 데모 기본값 `user01`로 고정
6. 실서비스에서는 `createdBy`를 Spring Security에서 추출하도록 대체
7. 배포 롤백 계획은 이번 범위에서 제외
8. DB 트랜잭션 롤백은 이번 범위에서 필수
9. 레거시 `.xls` 지원은 이번 범위에서 완전 제외
