# `@ExcelCompositeUnique` 선언 오류 Fail-Fast 메모

## 배경

`WithinFileUniqueConstraintValidator`의 복합 유니크 검사 로직은
`@ExcelCompositeUnique(fields = {...})`에 선언된 필드명을 리플렉션으로 조회한다.

기존 구현은 선언된 필드가 DTO에 없을 때 `NoSuchFieldException`을 `log.warn`으로만 남기고 계속 진행했다.
이 경우 복합 유니크 검증이 일부 필드만으로 수행되거나, 의도한 검증이 사실상 깨져도 원인을 즉시 알기 어려웠다.

리뷰 포인트는 다음과 같았다.

- 리플렉션 필드 수집 시점에 선언 무결성을 함께 검증해야 한다.
- DTO에 없는 필드를 참조하는 `@ExcelCompositeUnique`는 사용자 데이터 오류가 아니라 개발자 설정 오류이다.
- 이런 경우 `warn`으로 넘기지 말고 문제를 즉시 드러내야 한다.

## 이번 변경 내용

`WithinFileUniqueConstraintValidator` 내부에서만 최소 범위로 수정했다.

- `@ExcelCompositeUnique` 필드 해석을 `resolveCompositeFieldsOrThrow(...)`로 분리했다.
- 필드 조회는 `findField(...)`를 통해 DTO 클래스와 상위 클래스까지 확인한다.
- 선언된 필드가 하나라도 없으면 즉시 `IllegalStateException`을 던진다.
- 예외 메시지에는 다음 정보를 포함한다.
  - DTO 클래스명
  - 누락된 필드명
  - 애너테이션에 선언된 전체 필드 목록
- 정상 선언인 경우에는 기존 복합 중복 검출 동작을 유지한다.

## 의도한 동작

이제 잘못된 `@ExcelCompositeUnique` 선언은 다음처럼 취급된다.

- 사용자 업로드 데이터 오류: 아님
- 개발자/설정 오류: 맞음
- 처리 방식: 즉시 실패(Fail Fast)

즉, 복합 유니크 검증이 불완전한 상태로 조용히 계속 진행되지 않는다.

## 테스트 반영

`WithinFileUniqueConstraintValidatorTest`에 잘못된 선언을 가진 테스트 DTO를 추가했다.

- 예시: `@ExcelCompositeUnique(fields = {"field1", "missingField"})`
- 기대 결과: `checkWithinFileUniqueness(...)` 호출 시 `IllegalStateException` 발생
- 검증 내용:
  - 예외 타입
  - 한국어 오류 메시지
  - DTO 클래스명 포함 여부
  - 누락 필드명 포함 여부

## 영향 범위

- 변경 범위는 `WithinFileUniqueConstraintValidator`와 해당 테스트에 한정했다.
- 별도의 startup validator, bean 초기화 검증, 전역 설정 검증 로직은 추가하지 않았다.
- 따라서 이 오류는 애플리케이션 부팅 시점이 아니라, 해당 validator가 실제로 호출되는 시점에 드러난다.
