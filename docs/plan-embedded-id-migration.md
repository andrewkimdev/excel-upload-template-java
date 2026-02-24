# Adopt `@EmbeddedId` For Tariff Entities (Hard Switch)

## Summary
이 문서는 샘플 프로젝트에서 의도적으로 breaking change를 허용하는 전제로, tariff 상세/요약 엔티티를 surrogate `id`에서 복합키(`@EmbeddedId`)로 한 번에 전환하는 계획이다.

본 프로젝트는 상위 프로젝트에서 cherry-pick하여 참고하는 용도이므로, 점진 이행(dual-write, backward compatibility, 단계적 스키마 병행)은 범위에서 제외한다.

## Current State Evidence
- 상세 엔티티는 `Long id` + 복합 유니크 제약 조합을 사용 중:
  - `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/entity/TariffExemption.java`
- 요약 엔티티는 `Long id` + 복합 유니크 제약 조합을 사용 중:
  - `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/entity/TariffExemptionSummary.java`
- 저장소 제네릭 PK 타입은 둘 다 `Long`:
  - `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/repository/TariffExemptionRepository.java`
  - `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/repository/TariffExemptionSummaryRepository.java`
- 업서트는 현재 자연키 조건 조회 메서드 기반:
  - `TariffExemptionService#upsertDetailWithRetry`
  - `TariffExemptionService#upsertSummaryWithRetry`

## Non-Goals
- 기존 `Long id`와 신규 복합키 동시 지원
- 기존 레포지토리 시그니처 호환 유지
- 점진 배포용 migration choreography

## Implementation Plan
1. Add ID value objects.
- `TariffExemptionId` 생성: `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`, `rowNumber`
- `TariffExemptionSummaryId` 생성: `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`
- 두 클래스 모두:
  - `@Embeddable`
  - `implements Serializable`
  - 안정적인 `equals/hashCode`
  - 키 컬럼 제약 보존을 위한 `@Column(name, nullable, length)` 명시

2. Refactor entities to `@EmbeddedId`.
- `TariffExemption`:
  - `Long id` 제거
  - 키 스칼라 필드(`comeYear`, `comeSequence`, `uploadSequence`, `equipCode`, `rowNumber`) 제거
  - `@EmbeddedId private TariffExemptionId id` 추가
  - 비키 필드는 유지
- `TariffExemptionSummary`:
  - 동일 패턴으로 `TariffExemptionSummaryId` 적용
- PK가 유일성을 보장하므로 기존 키 관련 `@UniqueConstraint` 제거

3. Refactor repositories.
- `TariffExemptionRepository` -> `JpaRepository<TariffExemption, TariffExemptionId>`
- `TariffExemptionSummaryRepository` -> `JpaRepository<TariffExemptionSummary, TariffExemptionSummaryId>`
- 자연키 파생 조회 메서드(`findByComeYearAnd...`)는 제거하고, 업서트 식별 조회는 `findById(embeddedKey)`로 통일
- 비즈니스 중복 검사용 `existsByItemNameAndSpecificationAndHsCode`는 유지

4. Refactor service upsert logic (동작 보존 필수).
- `TariffExemptionService`에 키 생성 헬퍼 추가:
  - `buildDetailId(commonData, rowNumber)`
  - `buildSummaryId(commonData)`
- 상세 업서트:
  - `repository.findById(detailId)` 조회
  - create path에서만 `entity.setId(detailId)` 설정
  - update path에서는 ID 변경 금지
- 요약 업서트도 동일하게 `summaryRepository.findById(summaryId)`로 전환
- 기존 `saveAndFlush + DataIntegrityViolationException retry` 패턴 유지
  - 용도: 동시성 경합 시 PK 충돌 재시도 안전장치(비즈니스 중복 검증 용도 아님)
- 감사 필드 로직(`approvedYn`, `createdBy`, `createdAt`) 및 한국어 예외 메시지 유지

5. Enforce hard switch cleanup.
- 코드베이스에서 tariff 도메인의 `Long id` 기반 참조 제거
- 더 이상 사용하지 않는 자연키 조회 메서드 및 관련 테스트 fixture 제거
- 문서에서 tariff 엔티티의 식별자 모델을 `EmbeddedId` 기준으로 갱신

## Public API / Interface / Type Changes
1. `TariffExemption.id: Long -> TariffExemptionId`
2. `TariffExemptionSummary.id: Long -> TariffExemptionSummaryId`
3. `TariffExemptionRepository` PK generic: `Long -> TariffExemptionId`
4. `TariffExemptionSummaryRepository` PK generic: `Long -> TariffExemptionSummaryId`
5. Repository 조회 기준: 자연키 파생 메서드 -> `findById(embeddedKey)`
6. REST 업로드 계약(`/api/excel/upload/{templateType}`, `commonData`) 변화 없음

## Tests And Scenarios
1. Regression baseline
- `./gradlew test` 수행하여 업로드/보안/검증 회귀가 없는지 확인

2. Tariff persistence behavior tests (필수)
- 상세:
  - 같은 `TariffExemptionId`로 두 번 저장 시 1회차 create, 2회차 update 검증
- 요약:
  - 같은 `TariffExemptionSummaryId` 재저장 시 update 검증
- 양쪽 모두 `findById(embeddedKey)` round-trip 검증

3. Integration behavior hardening (필수)
- 동일 파일 + 동일 `commonData` 재업로드 시 `rowsCreated`/`rowsUpdated` 전환 검증
- 기존 `.xlsx` 허용, `.xls` 거부, `commonData` strict parsing 검증 유지

4. Mapping/schema verification (권장, 샘플 품질 향상)
- `@DataJpaTest`로 다음 검증:
  - PK가 복합키로 매핑되는지
  - surrogate `id` 컬럼이 생성되지 않는지

## Acceptance Criteria
1. Tariff 상세/요약 엔티티 모두 `@EmbeddedId`를 사용한다.
2. Tariff 저장소 PK 타입이 `Long`이 아닌 embedded key 타입이다.
3. `TariffExemptionService` 업서트가 `findById(embeddedKey)` 기반으로 동작한다.
4. 기존 업로드 계약 및 보안/검증 정책은 유지된다.
5. 전체 테스트(`./gradlew test`)가 통과한다.

## Tradeoffs
1. 장점
- 실제 식별자 모델과 JPA 매핑이 일치
- 업서트 식별 semantics가 명확해짐
- 샘플 코드의 이식성/참고 가치 상승

2. 단점
- 키 타입/테스트 fixture 보일러플레이트 증가
- 복합 PK로 인한 쿼리/인덱스 표현 복잡성 증가

## Assumptions
1. 본 변경은 breaking change를 허용한다.
2. 점진 이행 없이 hard switch가 기본 전략이다.
3. 범위는 tariff 상세/요약 엔티티 및 직접 연계 코드로 한정한다.
4. 사용자/로그 메시지의 한국어 정책은 유지한다.
