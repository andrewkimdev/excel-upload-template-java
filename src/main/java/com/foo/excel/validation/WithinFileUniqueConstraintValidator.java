package com.foo.excel.validation;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.util.ExcelColumnUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
/**
 * 업로드된 단일 Excel 파일 내부에서 중복값 제약조건을 검사하는 검증기이다.
 *
 * <p>이 클래스는 두 종류의 파일 내부 중복을 감지한다.
 *
 * <p>첫째, {@link ExcelUnique}가 선언된 단일 필드의 파일 내부 중복 여부를 검사한다.
 *
 * <p>둘째, {@link ExcelCompositeUnique}가 선언된 복합 키 조합의 파일 내부 중복 여부를 검사한다.
 *
 * <p>검사 결과는 각 행 번호를 기준으로 {@link RowError}에 누적되며, 실제 사용자에게는 셀 단위의
 * {@link CellError} 목록으로 반환된다.
 *
 * <p>단, {@link ExcelCompositeUnique} 선언 자체가 잘못되어 DTO에 존재하지 않는 필드를 참조하는 경우,
 * 이는 사용자 입력 오류가 아니라 개발자 설정 오류로 간주하고 즉시 예외를 발생시킨다.
 *
 * <p>주의할 점은 이 검증기는 "파일 내부 중복"만 본다는 것이다. 즉, DB에 이미 존재하는 값과의 충돌은
 * 다른 검증 단계에서 처리해야 한다.
 */
public class WithinFileUniqueConstraintValidator {
  private static final ExcelColumnRef UNKNOWN_COLUMN_REF = ExcelColumnRef.unknown();

  /**
   * DTO 목록 전체를 순회하면서 파일 내부 중복 제약조건을 점검한다.
   *
   * <p>검사는 단일 컬럼 기준 중복 검사와 복합 키 기준 중복 검사로 나누어 수행한다. 복합 키 선언이
   * 잘못되어 있으면 즉시 예외를 발생시켜 잘못된 설정을 조기에 드러낸다.
   *
   * @param rows 업로드 파일에서 파싱된 DTO 행 목록
   * @param dtoClass 검증 대상 DTO 타입
   * @param sourceRowNumbers DTO 목록과 같은 인덱스를 갖는 원본 Excel 행 번호 목록
   * @param <T> 검증 대상 DTO 타입
   * @return 누적된 행별 오류 목록
   */
  public <T> List<RowError> checkWithinFileUniqueness(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers) {
    return checkWithinFileUniqueness(rows, dtoClass, sourceRowNumbers, Integer.MAX_VALUE);
  }

  public <T> List<RowError> checkWithinFileUniqueness(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, int maxErrorRows) {
    // 여러 셀 오류를 행 번호 기준으로 모았다가 마지막에 RowError 목록으로 변환한다.
    RowErrorAccumulator errors = new RowErrorAccumulator();

    // @ExcelUnique(checkWithinFile = true) 대상 필드를 먼저 검사한다.
    checkSingleFieldUniqueness(rows, dtoClass, sourceRowNumbers, errors, maxErrorRows);
    // 클래스 레벨의 @ExcelCompositeUnique 선언도 이어서 검사한다.
    if (!hasReachedErrorLimit(errors, maxErrorRows)) {
      checkCompositeUniqueness(rows, dtoClass, sourceRowNumbers, errors, maxErrorRows);
    }

    return errors.toList();
  }

  /**
   * 단일 필드 기준의 파일 내부 중복을 검사한다.
   *
   * <p>{@link ExcelUnique}가 붙어 있고 {@code checkWithinFile()}이 활성화된 필드만 대상으로 한다.
   * 이미 등장한 값을 다시 만나면 현재 행에 중복 오류를 추가한다.
   *
   * @param rows 업로드 행 DTO 목록
   * @param dtoClass DTO 클래스
   * @param sourceRowNumbers 원본 Excel 행 번호 목록
   * @param errors 오류 누적기
   * @param <T> DTO 타입
   */
  private <T> void checkSingleFieldUniqueness(
      List<T> rows,
      Class<T> dtoClass,
      List<Integer> sourceRowNumbers,
      RowErrorAccumulator errors,
      int maxErrorRows) {

    // DTO에 선언된 모든 필드를 훑으면서 파일 내부 중복 검사 대상인지 확인한다.
    for (Field field : dtoClass.getDeclaredFields()) {
      ExcelUnique uniqueAnnotation = field.getAnnotation(ExcelUnique.class);
      if (uniqueAnnotation == null || !uniqueAnnotation.checkWithinFile()) {
        // 파일 내부 중복 검사를 요구하지 않는 필드는 건너뛴다.
        continue;
      }

      // private 필드도 리플렉션으로 읽을 수 있도록 접근을 허용한다.
      field.setAccessible(true);
      ExcelColumn excelColumn = field.getAnnotation(ExcelColumn.class);

      // 키는 실제 셀 값, 값은 그 값이 처음 등장한 원본 Excel 행 번호이다.
      Map<Object, Integer> seenValues = new HashMap<>();

      // DTO 목록과 sourceRowNumbers는 동일한 인덱스 체계를 가진다고 가정한다.
      for (int i = 0; i < rows.size(); i++) {
        if (hasReachedErrorLimit(errors, maxErrorRows)) {
          return;
        }
        T row = rows.get(i);
        Object value;
        try {
          // 현재 행 DTO에서 대상 필드 값을 꺼낸다.
          value = field.get(row);
        } catch (IllegalAccessException e) {
          // setAccessible(true)를 호출했으므로 일반적으로 오지 않지만,
          // 예외가 발생해도 전체 검증을 중단하지 않고 해당 값만 무시한다.
          continue;
        }

        if (value == null) {
          // null은 중복 비교에서 제외한다.
          continue;
        }

        int currentRowNum = sourceRowNumbers.get(i);

        if (seenValues.containsKey(value)) {
          // 이미 같은 값이 나온 적 있다면, 첫 등장 행 번호를 메시지에 포함해 현재 행에 오류를 단다.
          int firstRowNum = seenValues.get(value);
          CellError cellError =
              CellError.builder()
                  .fieldName(field.getName())
                  .headerName(excelColumn != null ? excelColumn.label() : field.getName())
                  .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                  .columnRef(excelColumn != null ? resolveColumnRef(excelColumn) : UNKNOWN_COLUMN_REF)
                  .rejectedValue(value)
                  .message(uniqueAnnotation.message() + " (행 " + firstRowNum + "과(와) 중복)")
                  .build();

          addErrorToRow(errors, currentRowNum, cellError);
        } else {
          // 처음 등장한 값만 기록하고, 이후 동일 값이 다시 나오면 그때 중복으로 처리한다.
          seenValues.put(value, currentRowNum);
        }
      }
    }
  }

  /**
   * 복합 키 기준의 파일 내부 중복을 검사한다.
   *
   * <p>DTO 클래스에 선언된 {@link ExcelCompositeUnique}를 모두 읽어 각 조합별로 중복을 판단한다.
   * 동일한 필드 조합이 다시 등장하면 현재 행에 오류를 기록한다.
   *
   * <p>복합 유니크 선언이 DTO 구조와 맞지 않으면 이는 개발자 설정 오류이므로 즉시 예외를 발생시킨다.
   *
   * @param rows 업로드 행 DTO 목록
   * @param dtoClass DTO 클래스
   * @param sourceRowNumbers 원본 Excel 행 번호 목록
   * @param errors 오류 누적기
   * @param <T> DTO 타입
   */
  private <T> void checkCompositeUniqueness(
      List<T> rows,
      Class<T> dtoClass,
      List<Integer> sourceRowNumbers,
      RowErrorAccumulator errors,
      int maxErrorRows) {

    // repeatable annotation을 고려하여 클래스 레벨의 모든 복합 유니크 선언을 가져온다.
    ExcelCompositeUnique[] compositeAnnotations =
        dtoClass.getAnnotationsByType(ExcelCompositeUnique.class);
    if (compositeAnnotations.length == 0) {
      // 복합 유니크 선언이 없다면 추가 작업이 없다.
      return;
    }

    for (ExcelCompositeUnique composite : compositeAnnotations) {
      // 복합 유니크 선언은 런타임 전에 올바르게 작성되어 있어야 하므로 엄격하게 해석한다.
      List<Field> fields = resolveCompositeFieldsOrThrow(dtoClass, composite);

      // 키는 복합 필드 값 목록, 값은 그 조합이 처음 등장한 원본 행 번호이다.
      Map<List<Object>, Integer> seenKeys = new HashMap<>();

      for (int i = 0; i < rows.size(); i++) {
        if (hasReachedErrorLimit(errors, maxErrorRows)) {
          return;
        }
        T row = rows.get(i);
        // 필드 순서를 유지한 값 목록 자체를 복합 키로 사용한다.
        List<Object> compositeKey = new ArrayList<>();

        for (Field f : fields) {
          try {
            compositeKey.add(f.get(row));
          } catch (IllegalAccessException e) {
            // 특정 필드 값을 읽지 못한 경우 null을 넣어 키 길이와 순서를 유지한다.
            compositeKey.add(null);
          }
        }

        int currentRowNum = sourceRowNumbers.get(i);

        if (seenKeys.containsKey(compositeKey)) {
          // 동일 복합 키가 이미 기록되어 있으면 현재 행에 오류를 추가한다.
          int firstRowNum = seenKeys.get(compositeKey);

          // 현재 구현은 복합 키 전체 오류를 첫 번째 필드 위치에 대표로 매핑한다.
          // UI나 오류 포맷은 이 첫 필드의 헤더명/컬럼 정보로 표시된다.
          Field firstField = fields.get(0);
          ExcelColumn excelColumn = firstField.getAnnotation(ExcelColumn.class);

          CellError cellError =
              CellError.builder()
                  .fieldName(firstField.getName())
                  .headerName(excelColumn != null ? excelColumn.label() : firstField.getName())
                  .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                  .columnRef(excelColumn != null ? resolveColumnRef(excelColumn) : UNKNOWN_COLUMN_REF)
                  .rejectedValue(compositeKey.toString())
                  .message(composite.message() + " (행 " + firstRowNum + "과(와) 중복)")
                  .build();

          addErrorToRow(errors, currentRowNum, cellError);
        } else {
          // 처음 등장한 조합은 기준값으로 저장한다.
          seenKeys.put(compositeKey, currentRowNum);
        }
      }
    }
  }

  /**
   * 복합 유니크 애너테이션에 선언된 필드 목록을 리플렉션으로 해석한다.
   *
   * <p>선언된 필드가 DTO에 존재하지 않으면 복합 유니크 검증 자체가 성립하지 않으므로 즉시 예외를 던진다.
   * 이 오류는 사용자 데이터 문제가 아니라 개발자 설정 문제를 의미한다.
   *
   * @param dtoClass 검증 대상 DTO 클래스
   * @param composite 복합 유니크 애너테이션
   * @return 정상 해석된 필드 목록
   * @throws IllegalStateException 애너테이션이 존재하지 않는 DTO 필드를 참조할 때
   */
  private List<Field> resolveCompositeFieldsOrThrow(
      Class<?> dtoClass, ExcelCompositeUnique composite) {
    List<Field> fields = new ArrayList<>();

    for (String fieldName : composite.fields()) {
      Field field = findField(dtoClass, fieldName);
      if (field == null) {
        throw invalidCompositeConfiguration(dtoClass, composite, fieldName);
      }

      field.setAccessible(true);
      fields.add(field);
    }

    if (fields.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "복합 유니크 검증 필드가 비어 있습니다. DTO=%s, 선언 필드=%s",
              dtoClass.getName(), List.of(composite.fields())));
    }

    return fields;
  }

  /**
   * DTO 클래스와 상위 클래스 체인을 따라가며 필드를 찾는다.
   *
   * @param dtoClass 시작 클래스
   * @param fieldName 찾을 필드명
   * @return 찾은 필드, 없으면 {@code null}
   */
  private Field findField(Class<?> dtoClass, String fieldName) {
    Class<?> current = dtoClass;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  /**
   * 잘못 선언된 복합 유니크 설정에 대한 예외를 생성한다.
   *
   * @param dtoClass 검증 대상 DTO 클래스
   * @param composite 복합 유니크 애너테이션
   * @param missingFieldName DTO에 존재하지 않는 필드명
   * @return 복합 유니크 설정 오류 예외
   */
  private IllegalStateException invalidCompositeConfiguration(
      Class<?> dtoClass, ExcelCompositeUnique composite, String missingFieldName) {
    return new IllegalStateException(
        String.format(
            "복합 유니크 검증 설정이 올바르지 않습니다. DTO=%s, 누락 필드=%s, 선언 필드=%s",
            dtoClass.getName(), missingFieldName, List.of(composite.fields())));
  }

  /**
   * 특정 행 번호에 셀 오류를 누적한다.
   *
   * @param errors 행 오류 누적기
   * @param rowNumber 원본 Excel 행 번호
   * @param cellError 추가할 셀 오류
   */
  private void addErrorToRow(RowErrorAccumulator errors, int rowNumber, CellError cellError) {
    errors.addCellError(rowNumber, cellError);
  }

  private boolean hasReachedErrorLimit(RowErrorAccumulator errors, int maxErrorRows) {
    return errors.size() >= maxErrorRows;
  }

  /**
   * {@link ExcelColumn}의 컬럼 문자를 0-based 인덱스로 변환한다.
   *
   * <p>컬럼 문자가 비어 있으면 위치 정보를 알 수 없으므로 {@code -1}을 반환한다.
   *
   * @param annotation Excel 컬럼 메타데이터
   * @return 0-based 컬럼 인덱스, 없으면 {@code -1}
   */
  private int resolveColumnIndex(ExcelColumn annotation) {
    return annotation.column().isEmpty() ? -1 : ExcelColumnUtil.letterToIndex(annotation.column());
  }

  /**
   * {@link ExcelColumn}에 선언된 컬럼 문자를 값 객체로 반환한다.
   *
   * <p>컬럼 문자가 없으면 사용자 표시용 대체값을 반환한다.
   *
   * @param annotation Excel 컬럼 메타데이터
   * @return 컬럼 참조 값 객체
   */
  private ExcelColumnRef resolveColumnRef(ExcelColumn annotation) {
    return annotation.column().isEmpty()
        ? UNKNOWN_COLUMN_REF
        : ExcelColumnRef.ofLetter(annotation.column());
  }
}
