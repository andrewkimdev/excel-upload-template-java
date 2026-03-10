# `ExcelColumnRef` 도입 메모

## 왜 바꿨는가

기존에는 컬럼 위치를 `String`으로 다루면서 한 값이 두 역할을 동시에 맡고 있었다.

- 내부 식별자: `A`, `B`, `AA`
- 사용자 표시 문자열: `A열`, `B열`, `?열`

이 상태에서는 코드 곳곳에서 `"열"`을 직접 붙이게 되고, 다음 문제가 반복해서 생긴다.

- 어떤 경로는 raw 값(`B`)을 저장하고, 어떤 경로는 표시값(`B열`)을 저장한다
- 메시지 포맷터가 `"열"`을 중복으로 붙여 `B열열` 같은 오류를 만들 수 있다
- 새 검증/리포트 코드를 추가할 때 어떤 형태를 넘겨야 하는지 매번 판단해야 한다
- `?`, `?열`, `B`, `B열`이 섞이면서 디버깅이 어려워진다

이번 변경의 목적은 **컬럼 식별자와 표시 문자열을 한 타입으로 캡슐화해서, 표시 규칙을 한 곳으로 모으는 것**이다.

## 무엇이 바뀌었는가

새 값 객체 [ExcelColumnRef.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/validation/ExcelColumnRef.java)를 추가했다.

이 타입은 두 가지 상태만 가진다.

- 알려진 컬럼: raw letter를 대문자로 정규화해서 저장 (`A`, `B`, `AA`)
- 위치를 특정할 수 없는 컬럼: `unknown()`

렌더링 규칙도 이 타입이 직접 가진다.

- `ExcelColumnRef.ofLetter("B").toString()` -> `B열`
- `ExcelColumnRef.unknown().toString()` -> `?열`

즉, 애플리케이션 코드가 `"열"`을 직접 붙이지 않고 `ExcelColumnRef`를 그대로 사용하도록 바뀌었다.

## 어떻게 적용했는가

다음 경로에서 `String columnLetter`를 `ExcelColumnRef`로 바꿨다.

- [CellError.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/validation/CellError.java)
- [ExcelParserService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/parse/ExcelParserService.java)
- [ColumnResolutionException.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/parse/ColumnResolutionException.java)
- [ExcelValidationService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/validation/ExcelValidationService.java)
- [WithinFileUniqueConstraintValidator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java)
- [AAppcarItemDbUniquenessChecker.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/templates/samples/aappcar/service/AAppcarItemDbUniquenessChecker.java)

메시지를 만드는 쪽도 같이 정리했다.

- [RowError.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/validation/RowError.java)
  - `columnRef + " " + message` 형태로만 조합한다
- [ColumnResolutionException.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/parse/ColumnResolutionException.java)
  - 한국어 메시지에서 더 이상 `"열"`을 직접 붙이지 않는다

## 중요한 예외: "컬럼이 없음" 상태

모든 경우를 `unknown()`으로 통합하지는 않았다.

[ColumnResolutionException.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/parse/ColumnResolutionException.java)에는 현재 두 가지 다른 상황이 있다.

- 고정 컬럼은 알고 있지만 헤더가 틀림: 예를 들어 `H열`
- 헤더 자동 탐지 자체가 실패해서 컬럼을 찾지 못함: 컬럼 없음

두 번째 경우는 기존 코드도 `null`로 구분하고 있었고, 메시지도 `"헤더 ... 컬럼을 찾을 수 없습니다"`로 따로 처리한다. 그래서 이번 리팩터링에서도 `ColumnResolutionException`은 `ExcelColumnRef`를 nullable로 유지해서 이 상태를 계속 구분했다.

즉:

- `ExcelColumnRef.unknown()` = 위치를 사용자에게 특정할 수 없음
- `null columnRef` in `ColumnResolutionException` = 컬럼 자체를 resolve하지 못함

이 구분은 현재 parser 흐름과 메시지 구조에 맞춘 것이다.

## 내부 메시지와 사용자 메시지의 분리

`ExcelColumnRef.toString()`은 사용자용 문자열을 반환한다. 따라서 내부/영문 로그 메시지나 예외 기본 메시지에서 raw 값이 필요하면 `rawLetter()`를 사용해야 한다.

예:

- 사용자 메시지: `B열 헤더가 일치하지 않습니다`
- 내부 메시지: `Column B header mismatch ...`

이 규칙을 지키면 표시 문자열과 내부 디버깅 문자열이 서로 오염되지 않는다.

## 이후 유지보수 규칙

앞으로 컬럼 위치를 다룰 때는 다음 규칙을 따른다.

1. 새 `CellError`를 만들 때 raw 문자열을 직접 넣지 말고 `ExcelColumnRef`를 만든다.
2. 애플리케이션 코드에서 `"열"`을 직접 붙이지 않는다.
3. 사용자 메시지에는 `ExcelColumnRef.toString()` 결과를 사용한다.
4. 내부 로그/예외 기본 메시지에는 필요할 때만 `rawLetter()`를 사용한다.
5. 컬럼을 아예 resolve하지 못한 상태와 단순 unknown 위치를 혼동하지 않는다.

## 회귀 방지

이번 변경과 함께 다음 종류의 테스트를 유지한다.

- `B열열`, `H열열`, `?열열` 같은 중복 접미사 방지
- `unknown()`이 항상 `?열`로 렌더링되는지 확인
- parser / validation / uniqueness / error report 경로에서 같은 표시 규칙을 쓰는지 확인

핵심은 하나다. **컬럼 문자열을 문자열처럼 다루지 않고 값 객체로 다루면, 포맷 규칙이 코드베이스 전체에서 자연스럽게 일관돼진다.**
