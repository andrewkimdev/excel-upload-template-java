package com.foo.excel.templates.samples.aappcar.mapper;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemCommonDataFormMapper {

  public AAppcarItemCommonData toCommonData(
      String comeYear,
      String comeOrder,
      String uploadSeq,
      String equipCode,
      String equipMean,
      String hsno,
      String spec,
      String taxRate,
      String filePath,
      String approvalYn,
      String approvalDate) {
    AAppcarItemCommonData commonData = new AAppcarItemCommonData();
    commonData.setComeYear(comeYear);
    commonData.setComeOrder(comeOrder);
    commonData.setUploadSeq(uploadSeq);
    commonData.setEquipCode(equipCode);
    commonData.setEquipMean(equipMean);
    commonData.setHsno(hsno);
    commonData.setSpec(spec);
    commonData.setTaxRate(parseTaxRate(taxRate));
    commonData.setFilePath(blankToNull(filePath));
    commonData.setApprovalYn(blankToNull(approvalYn));
    commonData.setApprovalDate(parseApprovalDate(approvalDate));
    return commonData;
  }

  private BigDecimal parseTaxRate(String taxRate) {
    try {
      return new BigDecimal(taxRate.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("taxRate 형식이 올바르지 않습니다.");
    }
  }

  private LocalDate parseApprovalDate(String approvalDate) {
    if (approvalDate == null || approvalDate.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(approvalDate.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("approvalDate 형식이 올바르지 않습니다. (YYYY-MM-DD)");
    }
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
