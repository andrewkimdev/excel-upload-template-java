# 업로드 검증 로직 가이드 (이식/적용용)

이 문서는 현재 코드베이스의 Excel 업로드 검증 로직을, 다른 프로젝트에 이식할 때 필요한 관점으로 정리한 문서다.

## 1. 검증 파이프라인 개요

실행 진입점은 `ExcelUploadRequestService`이며, 전체 흐름은 아래 순서로 진행된다.

1. 요청 레벨 검증
2. 파일 저장/보안 검증
3. 행 수 사전 점검(pre-count)
4. 파싱 및 컬럼 해석 검증
5. 데이터 유효성 검증(JSR-380 + 파일 내 중복)
6. DB 중복 검증(템플릿 선택)
7. 오류 병합 후 성공 저장 또는 오류 리포트 생성

핵심 오케스트레이션은 `ExcelImportOrchestrator#doProcess`에 집중되어 있다.

## 2. 요청 레벨 검증

대상 클래스:
- `ExcelUploadRequestService`

검증 내용:
- 파일 크기 제한: `checkFileSize`
  - `excel.import.max-file-size-mb` 초과 시 `MaxUploadSizeExceededException`
- `commonData` 필수 여부/형식 검증
  - JSON 모드 업로드 시 `commonData` 누락/공백 금지
  - 엄격 역직렬화 적용:
    - 알 수 없는 필드 거부 (`FAIL_ON_UNKNOWN_PROPERTIES`)
    - 텍스트 필드에 대한 스칼라 강제 변환 거부 (`ALLOW_COERCION_OF_SCALARS` 비활성 + Textual coercion fail)
  - Bean Validation 위반 시 첫 번째 위반 메시지로 `IllegalArgumentException`

이식 포인트:
- API 계약을 느슨하게 가져가려면 strict mapper 설정을 의도적으로 완화해야 한다.
- 현재 구현은 `commonData` 오류를 빠르게 400으로 반환하는 설계다.

## 3. 파일/보안 검증

대상 클래스:
- `ExcelUploadFileService`
- `SecureExcelUtils`

검증 내용:
- 확장자 정책:
  - `.xls` 명시 거부
  - `.xlsx`만 허용
- 파일명 정규화:
  - 경로 분리자 제거, 제어문자 제거, 위험 문자 치환
  - 연속 점(`..`) 정규화
  - 확장자 재검증
- 매직 바이트 검증:
  - `.xlsx` ZIP 시그니처(`PK...`) 확인
- 보안 워크북 오픈:
  - Apache POI 보안 제한 적용
  - XXE/Zip Bomb 방어 설정 사용

이식 포인트:
- 파일 저장소를 로컬 디스크가 아닌 오브젝트 스토리지로 바꿔도, “정규화 → 저장 → 매직바이트 검증” 순서는 유지하는 것이 안전하다.

## 4. 행 수 제한(2단계)

대상 클래스:
- `ExcelImportOrchestrator`
- `SecureExcelUtils#countRows`
- `ExcelParserService`

검증 내용:
- 1차(pre-count): SAX/StAX 기반 대략 행 수 확인
  - 임계값 = `maxRows + (dataStartRow - 1) + preCountBuffer`
  - 초과 시 즉시 실패 반환(전체 파싱 생략)
- 2차(parse 후): 실제 파싱 결과 행 수 재확인
  - `maxRows` 초과 시 실패 반환
- 파서 내부 조기 중단:
  - 파싱 중 `rows.size() > maxRows`면 조기 종료

이식 포인트:
- 대용량 파일이 잦은 시스템은 pre-count 버퍼를 업무 포맷에 맞게 조정해야 한다.

## 5. 파싱 단계 검증

대상 클래스:
- `ExcelParserService`
- `ColumnResolutionException`, `ColumnResolutionBatchException`

검증 내용:
- 헤더 행 존재 검증
- 컬럼 해석:
  - 고정 컬럼(`@ExcelColumn.column`) + 헤더 매칭 검증
  - 자동 탐지(헤더 텍스트 기반)
  - 필수 컬럼 미해석/불일치 시 배치 예외로 누적 후 실패
  - 선택 컬럼(`required=false`)은 미해석 시 스킵
- 헤더 매칭 모드:
  - EXACT / CONTAINS / STARTS_WITH / REGEX
- 데이터 행 처리:
  - 빈 행 스킵
  - 푸터 마커 발견 시 중단
  - 병합 셀 값 해석
- 타입 변환 실패:
  - 셀 단위 parse error로 누적(`RowError`/`CellError`)

이식 포인트:
- 템플릿 변경 시 가장 먼저 `@ExcelColumn`과 `ExcelImportConfig`(header row, data start row, footer marker) 정합성을 맞춰야 한다.

## 6. 비즈니스 유효성 검증

대상 클래스:
- `ExcelValidationService`
- `WithinFileUniqueConstraintValidator`
- (옵션) `DatabaseUniquenessChecker` 구현체

검증 내용:
- 1차: JSR-380 Bean Validation
  - `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@DecimalMin/@DecimalMax` 등
- 2차: 파일 내 유일성
  - `@ExcelUnique` 단일 필드 중복
  - `@ExcelCompositeUnique` 복합키 중복
- 3차: DB 유일성(템플릿별 선택)
  - `TemplateDefinition`에 checker가 있으면 실행
  - 없으면 빈 오류 목록 반환
- 오류 병합:
  - 파싱 오류 + JSR-380 오류 + 파일내/DB 중복 오류를 같은 `ExcelValidationResult`로 합침

이식 포인트:
- DB 중복 검증 로직은 템플릿 도메인 키 전략(자연키/복합키/업로드 단위 키)에 맞춰 별도 구현해야 한다.

## 7. 실패 응답 및 오류 노출 정책

대상 클래스:
- `ExcelApiExceptionHandler`

정책:
- `IllegalArgumentException` → 400 + 메시지 전달
- `SecurityException` → 400 + 고정 보안 실패 메시지(내부 상세 숨김)
- `MaxUploadSizeExceededException` → 413
- 멀티파트 오류 → 400
- 기타 예외 → 500 + 일반 메시지(내부 상세 숨김)

추가 동작:
- 검증 실패(행 오류 존재) 시 오류 리포트 Excel 생성
  - `_ERRORS` 컬럼 추가
  - 오류 셀 하이라이트
  - 수식 인젝션 방지 문자열 정규화
  - 원본 파일명 `.meta` 저장

## 8. 템플릿 이식 체크리스트

새 프로젝트에 적용할 때 아래를 한 세트로 맞춰야 한다.

1. DTO
   - `@ExcelColumn` + JSR-380
   - 필요 시 `@ExcelUnique` / `@ExcelCompositeUnique`
2. `ExcelImportConfig`
   - 헤더 행/데이터 시작 행/시트 인덱스/푸터 마커
3. `CommonData` DTO
   - Bean Validation
   - `getCustomId()`가 non-blank 보장
4. `PersistenceHandler<T, C>`
   - `saveAll(List<T>, List<Integer>, C)` 구현
5. (선택) `DatabaseUniquenessChecker<T, C>`
6. `TemplateDefinition<T, C>` 빈 등록
   - `commonDataClass` 반드시 지정

## 9. 운영 시 주의사항

- 사용자 응답 메시지는 한국어 정책을 유지한다.
- 예기치 못한 시스템 예외 상세는 외부로 노출하지 않는다.
- `th:text` 기반 escaping을 유지해 UI 렌더링 시 XSS 위험을 줄인다.
- 대용량 처리 환경에서는 `maxRows`, `preCountBuffer`, 파일 크기 제한을 함께 조정한다.

