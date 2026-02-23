# Class Naming Alignment Plan (Implemented)

## Summary
본 문서는 클래스 네이밍 정비 작업의 실행 결과를 기록한다.
핵심 목표였던 "역할-이름 정합성"을 유지한 채, 기능/계약 변화 없이 리네이밍을 완료했다.

## Mapping Table (Previous -> Current)
| 구분 | 기존 이름 | 현재 이름 | 상태 |
|---|---|---|---|
| Class | `ExcelConversionService` | `ExcelUploadFileService` | Applied |
| Method | `ensureXlsxFormat(MultipartFile, Path)` | `storeAndValidateXlsx(MultipartFile, Path)` | Applied |
| Class | `UniqueConstraintValidator` | `WithinFileUniqueConstraintValidator` | Applied |
| Class | `ExcelUploadController` | `ExcelFileController` | Applied |
| Test Class | `ExcelConversionServiceTest` | `ExcelUploadFileServiceTest` | Applied |
| Test Class | `UniqueConstraintValidatorTest` | `WithinFileUniqueConstraintValidatorTest` | Applied |

## Evidence (Current State)
- 업로드 파일 저장/검증 서비스:
  - `src/main/java/com/foo/excel/service/ExcelUploadFileService.java`
- 오케스트레이터 주입/호출:
  - `src/main/java/com/foo/excel/service/ExcelImportOrchestrator.java`
- within-file 유니크 검증기:
  - `src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java`
- 컨트롤러:
  - `src/main/java/com/foo/excel/controller/ExcelFileController.java`
- 리네임된 테스트:
  - `src/test/java/com/foo/excel/service/ExcelUploadFileServiceTest.java`
  - `src/test/java/com/foo/excel/validation/WithinFileUniqueConstraintValidatorTest.java`

## Hardened Quality Gates (Result)
1. 빌드/테스트
- `./gradlew test` 통과

2. Stale symbol zero-tolerance
- `ExcelConversionService` 잔존 없음
- `ensureXlsxFormat` 잔존 없음
- `UniqueConstraintValidator` 잔존 없음
- `ExcelUploadController` 잔존 없음

3. 정책/계약 회귀 없음
- `.xlsx` 허용 / `.xls` 거부 유지
- magic-byte 검증 유지
- 내부 예외 상세 비노출 유지
- REST/Thymeleaf endpoint 경로 유지

## Markdown Synchronization
리네임된 클래스명을 참조하는 Markdown 파일 동기화 완료:
- `README.md`
- `CLAUDE.md`
- `HOW_TO_UNDERSTAND_THE_UPLOAD_PIPELINE.md`
- `docs/plan-tariff-upload-entity-a-b-transaction-20260223-021012.md`
- `docs/plan-class-naming-alignment-20260223-070050.md`

## Assumptions
1. 본 변경은 리팩터링(행동 불변) 범위다.
2. 패키지 구조/엔드포인트/요청응답 계약은 유지한다.
