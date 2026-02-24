# Template CommonData Hard-Switch PoC Plan

## Summary
이 문서는 `UploadCommonData` 고정 계약을 제거하고 템플릿별 제네릭 `commonData`로 전환하는 PoC 구현 계획을 정의한다.

- 적용 정책: hard switch (하위호환 계층 없음)
- PoC 정책: breaking change 허용
- 문서 충돌 해석(본 PoC 범위): 본 계획 문서를 우선 적용하고, 충돌 나는 기존 문서/계약은 본 계획 기준으로 정렬한다.
- 엔드포인트 형태 유지: `POST /api/excel/upload/{templateType}`, multipart(`file`, `commonData`)
- `commonData`는 템플릿별 DTO 스키마로 strict 파싱/검증
- 보안 체인(`.xlsx only`, magic-byte, secure workbook, row pre-count/limit) 유지
- 예기치 않은 시스템 오류는 내부 상세 미노출 유지
- 사용자 메시지/로그/주요 주석 한국어 정책은 점진 적용(이번 PoC에서 변경/신규 라인 우선)

## Final Decisions
1. `commonData` 타입은 템플릿별 DTO를 사용한다.
2. SPI와 API 스키마는 즉시 breaking change로 반영한다.
3. 단, REST 경로/파트명(`file`, `commonData`)은 유지한다.
4. `createdBy`, `approvedYn`는 요청으로 받지 않고 서버가 강제 주입한다.
5. strict parsing 규칙(`FAIL_ON_UNKNOWN_PROPERTIES`, scalar coercion 차단)은 유지한다.
6. Thymeleaf `/upload`도 `UploadCommonData` 의존을 제거하고 템플릿별 commonData 흐름으로 맞춘다.
7. `CommonData`는 최소 제약의 마커 인터페이스(빈 인터페이스)로 도입한다.
8. 제네릭 타입 안정성은 런타임 최소 검증(`commonDataClass.isInstance`)을 추가해 보완한다.
9. `.xls`는 업로드 경계에서만 명시적으로 거부하고, 유틸 계층은 `.xlsx` 전용으로 단순화한다.

## Public Interfaces and Type Changes
1. `PersistenceHandler<T>` -> `PersistenceHandler<T, C extends CommonData>`
2. `saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`
   -> `saveAll(List<T> rows, List<Integer> sourceRowNumbers, C commonData)`
3. `TemplateDefinition<T>` -> `TemplateDefinition<T, C extends CommonData>`
4. `TemplateDefinition`에 `Class<C> commonDataClass` 필드 추가
5. 오케스트레이터 내부 타입 흐름을 `T/C`로 정렬

## Implementation Steps
1. 공통 마커 도입
- `com.foo.excel.service.contract.CommonData` 인터페이스 추가 (마커 인터페이스)

2. Tariff commonData 분리
- `TariffExemptionCommonData` DTO 추가
- 필드: `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`
- 검증: 기존 `@NotBlank` + 한국어 메시지 유지
- 서버 강제값: `createdBy=user01`, `approvedYn=N` (`@JsonIgnore` getter)

3. 코어 SPI 제네릭화
- `PersistenceHandler<T, C>`로 변경
- Tariff 서비스 시그니처를 `PersistenceHandler<TariffExemptionDto, TariffExemptionCommonData>`로 변경

4. 템플릿 정의 제네릭화
- `TemplateDefinition<T, C>`로 변경
- `commonDataClass` 생성자 인자/필드/게터 추가
- Tariff 템플릿 빈에서 `TariffExemptionCommonData.class` 바인딩

5. 오케스트레이터 제네릭 흐름 반영
- 템플릿 레지스트리 타입을 `List<TemplateDefinition<?, ?>>`로 유지
- 템플릿 조회 후 typed helper로 위임해 `C commonData`를 persistence까지 전달
- `commonDataClass.isInstance(commonData)` 최소 런타임 가드 추가 후 캐스팅
- 기존 업로드 처리 파이프라인 동작은 유지

6. 컨트롤러 commonData 파싱 리팩터링 (REST)
- 고정 `UploadCommonData` 파싱 제거
- 템플릿 정의의 `commonDataClass` 기반 역직렬화로 변경
- strict mapper + Bean Validation 적용
- 오류 응답 정책 유지:
  - 누락/형식/검증 실패 -> 400
  - 예상치 못한 시스템 오류 -> 내부 상세 노출 없이 일반 메시지

7. Thymeleaf `/upload` 경로 정렬
- `UploadCommonData` 직접 생성/검증 제거
- tariff 템플릿 기준 템플릿별 commonData 생성/검증으로 변경
- 화면 입력 항목은 1단계에서 tariff 고정 폼 유지

8. 구 클래스 정리
- `UploadCommonData` 사용처 제거
- hard switch 기준으로 `UploadCommonData` 클래스 삭제

9. 한국어 정합성 정리
- 이번 변경에서 수정/신규로 추가되는 업로드 핵심 경로 로그/주석/메시지는 한국어로 작성
- 기존 코드의 미수정 영문 로그/주석 일괄 치환은 본 PoC 범위에서 제외(점진 전환)

10. 문서 동기화
- `README.md`의 업로드 계약, 템플릿 추가 가이드를 `TemplateDefinition<T, C>` 기준으로 갱신
- strict `commonData` 정책을 템플릿별 DTO 기준으로 명시
- 테스트/아키텍처 인벤토리와 실제 클래스/시그니처를 동기화

11. `.xls` 유틸 경로 정리
- `SecureExcelUtils`의 `.xls` 허용/파싱/검증 분기 제거
- 유틸 계층은 `.xlsx`만 처리하도록 단순화
- `.xls` 거부는 업로드 경계(파일명/형식 검증)에서 유지

## Test Plan
1. 계약 테스트 (`TariffUploadPlanContractTest`)
- `UploadCommonData` 고정 시그니처 검증 제거
- `PersistenceHandler<T, C>` 시그니처 및 `TemplateDefinition`의 `commonDataClass` 보유 검증 추가

2. 통합 테스트 (`ExcelImportIntegrationTest`)
- `commonData` 누락 -> 400
- 템플릿 required 누락/빈값 -> 400
- unknown field -> 400
- scalar coercion 입력 -> 400
- `.xls` 업로드 -> 400
- 정상 `.xlsx + commonData` -> 성공 카운트 검증

3. 회귀 테스트
- 보안 체인 유지 확인:
  - filename sanitization
  - magic-byte validation
  - secure workbook opening
  - row pre-count / row-limit checks
- `.xls` 거부 경로는 업로드 경계에서 유지되는지 확인
- `SecureExcelUtils`는 `.xlsx` 전용 동작으로 정리되었는지 확인
- 내부 예외 상세정보 미노출 확인
- 이번 변경 라인의 한국어 메시지/로그 정합성 확인

4. 실행
- `./gradlew test`

## Acceptance Criteria
1. 런타임 코드에 `UploadCommonData` 의존이 남아있지 않다.
2. Tariff 업로드 경로(REST/Thymeleaf)에서 템플릿 타입에 맞는 `commonData` DTO가 파싱/검증/전달된다.
3. strict unknown-field 거부와 scalar coercion 차단이 유지된다.
4. persistence handler는 typed `commonData`를 받는다.
5. 기존 보안 검증/행 제한/에러 마스킹 동작이 유지된다.
6. 업로드 핵심 경로의 영문 로그/주석이 한국어로 정리된다.
   - 단, 본 PoC에서는 수정/신규 라인에 한해 적용한다.
7. README와 테스트 계약이 구현과 일치한다.

## Assumptions and Defaults
1. 본 프로젝트는 PoC 성격이므로 breaking change를 즉시 반영한다.
2. 1단계에서 템플릿은 tariff 하나이므로 Thymeleaf 폼은 고정 입력을 유지한다.
3. `CommonData`는 현재 마커 인터페이스로 두고, 공통 getter 강제는 다중 템플릿 요구가 생길 때 재평가한다.
4. `createdBy`는 데모 기본값 `user01`, `approvedYn`는 `N`으로 고정한다.
5. `.xls`는 유틸 계층에서 처리하지 않고 업로드 경계에서만 거부한다.
