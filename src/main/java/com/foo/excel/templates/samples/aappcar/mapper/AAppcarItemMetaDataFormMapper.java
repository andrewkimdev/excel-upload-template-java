package com.foo.excel.templates.samples.aappcar.mapper;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemMetaDataFormMapper {

  public AAppcarItemMetaData toMetaData(
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
    AAppcarItemMetaData metaData = new AAppcarItemMetaData();
    metaData.setComeYear(comeYear);
    metaData.setComeOrder(comeOrder);
    metaData.setUploadSeq(uploadSeq);
    metaData.setEquipCode(equipCode);
    metaData.setEquipMean(equipMean);
    metaData.setHsno(hsno);
    metaData.setSpec(spec);
    metaData.setTaxRate(parseTaxRate(taxRate));
    metaData.setFilePath(blankToNull(filePath));
    metaData.setApprovalYn(blankToNull(approvalYn));
    metaData.setApprovalDate(parseApprovalDate(approvalDate));
    return metaData;
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
