package com.foo.excel.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 파일 처리를 위한 보안 유틸리티.
 *
 * <p>XXE(XML External Entity) 공격과 Zip Bomb 공격을 방어한다.
 */
public final class SecureExcelUtils {

  // 단일 레코드 최대 바이트 수(100MB)
  private static final int MAX_RECORD_LENGTH = 100_000_000;

  // 바이트 배열 할당 최대 크기(200MB)
  private static final int MAX_BYTE_ARRAY_SIZE = 200_000_000;

  // 셀 내 텍스트 최대 크기(10MB)
  private static final int MAX_TEXT_SIZE = 10_000_000;

  // XLSX 매직 바이트(ZIP 형식: PK)
  private static final byte[] XLSX_MAGIC = {0x50, 0x4B, 0x03, 0x04};

  static {
    // Apache POI 보안 제한을 전역으로 설정
    IOUtils.setByteArrayMaxOverride(MAX_BYTE_ARRAY_SIZE);
  }

  private SecureExcelUtils() {
    // 유틸리티 클래스
  }

  /**
   * 보안 보호를 적용하여 파일에서 Workbook을 생성한다.
   *
   * <p>XXE와 Zip Bomb 공격을 방어한다.
   *
   * @param file 열 Excel 파일
   * @return Workbook 인스턴스
   * @throws IOException 파일을 읽을 수 없거나 유효하지 않은 경우
   * @throws SecurityException 파일이 보안 검증을 통과하지 못한 경우
   */
  public static Workbook createWorkbook(File file) throws IOException {
    validateFileContent(file.toPath());

    try {
      OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ);
      return new XSSFWorkbook(pkg);
    } catch (Exception e) {
      throw new IOException("Failed to open XLSX file securely: " + e.getMessage(), e);
    }
  }

  /**
   * 보안 보호를 적용하여 Path에서 Workbook을 생성한다.
   *
   * @param path Excel 파일 경로
   * @return Workbook 인스턴스
   * @throws IOException 파일을 읽을 수 없거나 유효하지 않은 경우
   * @throws SecurityException 파일이 보안 검증을 통과하지 못한 경우
   */
  public static Workbook createWorkbook(Path path) throws IOException {
    return createWorkbook(path.toFile());
  }

  /**
   * 파일 내용이 확장자와 일치하는지 검증한다.
   *
   * <p>위장된 악성 파일을 막기 위해 매직 바이트를 확인한다.
   *
   * @param path 검증할 파일
   * @throws IOException 파일을 읽을 수 없는 경우
   * @throws SecurityException 파일 내용이 기대 형식과 일치하지 않는 경우
   */
  public static void validateFileContent(Path path) throws IOException {
    String fileName = path.getFileName().toString().toLowerCase();
    byte[] header = readFileHeader(path, 8);

    if (fileName.endsWith(".xlsx")) {
      if (!matchesMagicBytes(header, XLSX_MAGIC)) {
        throw new SecurityException(
            "File content does not match XLSX format. "
                + "The file may be corrupted or disguised.");
      }
    } else {
      throw new SecurityException("Only .xlsx files are supported.");
    }
  }

  /**
   * InputStream이 유효한 Excel 내용을 포함하는지 검증한다.
   *
   * <p>참고: 이 과정은 스트림 바이트를 소모하므로 mark/reset 또는 새 스트림과 함께 사용해야 한다.
   *
   * @param inputStream 검증할 스트림
   * @param expectedExtension 기대 파일 확장자(xlsx 또는 xls)
   * @throws IOException 스트림을 읽을 수 없는 경우
   * @throws SecurityException 내용이 기대 형식과 일치하지 않는 경우
   */
  public static void validateStreamContent(InputStream inputStream, String expectedExtension)
      throws IOException {
    byte[] header = new byte[8];
    int bytesRead = inputStream.read(header);

    if (bytesRead < 4) {
      throw new SecurityException("File is too small to be a valid Excel file");
    }

    if ("xlsx".equalsIgnoreCase(expectedExtension)) {
      if (!matchesMagicBytes(header, XLSX_MAGIC)) {
        throw new SecurityException("File content does not match XLSX format");
      }
    } else {
      throw new SecurityException("Only xlsx validation is supported");
    }
  }

  /**
   * 경로 순회 공격 방지를 위해 파일명을 정규화한다.
   *
   * <p>디렉터리 구성 요소와 위험 문자를 제거한다.
   *
   * @param originalFilename 사용자 입력의 원본 파일명
   * @return 파일 작업에 안전하게 사용할 수 있는 정규화된 파일명
   * @throws IllegalArgumentException 정규화 후 파일명이 null이거나 비어 있는 경우
   */
  public static String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalArgumentException("Filename cannot be null or empty");
    }

    // 경로 구성 요소를 제거하고 파일명만 추출
    String filename = originalFilename;

    // Unix와 Windows 경로 구분자를 모두 처리
    int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      filename = filename.substring(lastSlash + 1);
    }

    // null 바이트와 기타 제어 문자 제거
    filename = filename.replaceAll("[\\x00-\\x1F\\x7F]", "");

    // 잠재적으로 위험한 문자 제거 또는 치환
    // 영숫자, 점, 하이픈, 밑줄, 공백, 한글만 허용
    filename =
        filename.replaceAll(
            "[^a-zA-Z0-9.\\-_\\s\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F]", "_");

    // 연속된 점 여러 개를 방지(예: 경로 순회용 "..")
    filename = filename.replaceAll("\\.{2,}", ".");

    // 앞뒤 점 및 공백 제거
    filename = filename.replaceAll("^[.\\s]+|[.\\s]+$", "");

    if (filename.isBlank()) {
      throw new IllegalArgumentException("Filename is invalid after sanitization");
    }

    // 파일명이 유효한 Excel 확장자를 갖는지 확인
    String lowerFilename = filename.toLowerCase();
    if (!lowerFilename.endsWith(".xlsx")) {
      throw new IllegalArgumentException("Invalid file extension");
    }

    return filename;
  }

  /**
   * Excel 셀에 안전하게 포함되도록 문자열 값을 정규화한다.
   *
   * <p>수식 인젝션 공격을 방지한다.
   *
   * @param value 정규화할 값
   * @return Excel 셀에 안전한 정규화 값
   */
  public static String sanitizeForExcelCell(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    // Excel에서 수식 해석을 유발할 수 있는 문자
    char firstChar = value.charAt(0);
    if (firstChar == '='
        || firstChar == '+'
        || firstChar == '-'
        || firstChar == '@'
        || firstChar == '\t'
        || firstChar == '\r'
        || firstChar == '\n') {
      // 수식 해석 방지를 위해 작은따옴표 접두어 추가
      return "'" + value;
    }

    return value;
  }

  /**
   * 경량 StAX 스트리밍으로 xlsx 시트의 행 수를 센다.
   *
   * <p>전체 워크북 DOM을 로드하지 않으므로 파일 크기와 무관하게 상수 메모리를 사용한다.
   *
   * @param xlsxFile 행 수를 셀 xlsx 파일
   * @param sheetIndex 0-based 시트 인덱스
   * @return 시트 XML 내 row 요소 개수
   * @throws IOException 파일을 읽을 수 없거나 시트를 찾을 수 없는 경우
   */
  public static int countRows(Path xlsxFile, int sheetIndex) throws IOException {
    try (OPCPackage pkg = OPCPackage.open(xlsxFile.toFile(), PackageAccess.READ)) {
      var reader = new XSSFReader(pkg);
      Iterator<InputStream> sheets = reader.getSheetsData();

      int currentSheet = 0;
      while (sheets.hasNext()) {
        try (InputStream sheetStream = sheets.next()) {
          if (currentSheet == sheetIndex) {
            return countRowElements(sheetStream);
          }
        }
        currentSheet++;
      }

      throw new IOException("Sheet index " + sheetIndex + " not found in workbook");
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Failed to count rows in xlsx file: " + e.getMessage(), e);
    }
  }

  private static int countRowElements(InputStream sheetStream) throws IOException {
    try {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

      XMLStreamReader xmlReader = factory.createXMLStreamReader(sheetStream);
      int rowCount = 0;
      while (xmlReader.hasNext()) {
        if (xmlReader.next() == XMLStreamConstants.START_ELEMENT
            && "row".equals(xmlReader.getLocalName())) {
          rowCount++;
        }
      }
      xmlReader.close();
      return rowCount;
    } catch (Exception e) {
      throw new IOException("Failed to parse sheet XML: " + e.getMessage(), e);
    }
  }

  private static byte[] readFileHeader(Path path, int length) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      byte[] header = new byte[length];
      int bytesRead = is.read(header);
      if (bytesRead < 4) {
        throw new IOException("File is too small to be a valid Excel file");
      }
      return header;
    }
  }

  private static boolean matchesMagicBytes(byte[] header, byte[] magic) {
    if (header.length < magic.length) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if (header[i] != magic[i]) {
        return false;
      }
    }
    return true;
  }
}
