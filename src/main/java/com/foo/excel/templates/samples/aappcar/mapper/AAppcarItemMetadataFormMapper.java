package com.foo.excel.templates.samples.aappcar.mapper;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemMetadataFormMapper {

  public AAppcarItemMetadata toMetadata(
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
    AAppcarItemMetadata metadata = new AAppcarItemMetadata();
    metadata.setComeYear(comeYear);
    metadata.setComeOrder(comeOrder);
    metadata.setUploadSeq(uploadSeq);
    metadata.setEquipCode(equipCode);
    metadata.setEquipMean(equipMean);
    metadata.setHsno(hsno);
    metadata.setSpec(spec);
    metadata.setTaxRate(parseTaxRate(taxRate));
    metadata.setFilePath(blankToNull(filePath));
    metadata.setApprovalYn(blankToNull(approvalYn));
    metadata.setApprovalDate(parseApprovalDate(approvalDate));
    return metadata;
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
