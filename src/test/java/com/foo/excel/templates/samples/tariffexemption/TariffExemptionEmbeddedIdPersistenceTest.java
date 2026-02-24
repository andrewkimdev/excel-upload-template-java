package com.foo.excel.templates.samples.tariffexemption;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.ExcelUploadApplication;
import com.foo.excel.service.contract.PersistenceHandler.SaveResult;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionCommonData;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionDto;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemption;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionId;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummary;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummaryId;
import com.foo.excel.templates.samples.tariffexemption.persistence.repository.TariffExemptionRepository;
import com.foo.excel.templates.samples.tariffexemption.persistence.repository.TariffExemptionSummaryRepository;
import com.foo.excel.templates.samples.tariffexemption.service.TariffExemptionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = ExcelUploadApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TariffExemptionEmbeddedIdPersistenceTest {

  @Autowired private TariffExemptionService service;

  @Autowired private TariffExemptionRepository repository;

  @Autowired private TariffExemptionSummaryRepository summaryRepository;

  @Test
  void saveAll_sameDetailId_createsThenUpdates_withFindByIdRoundTrip() {
    TariffExemptionCommonData commonData = commonData();

    TariffExemptionDto first = dto("Item1", "Model-A");
    SaveResult firstResult = service.saveAll(List.of(first), List.of(7), commonData);

    TariffExemptionId detailId = new TariffExemptionId("2026", "001", "U001", "EQ-01", 7);
    TariffExemptionSummaryId summaryId =
        new TariffExemptionSummaryId("2026", "001", "U001", "EQ-01");

    assertThat(firstResult.created()).isEqualTo(1);
    assertThat(firstResult.updated()).isZero();
    assertThat(repository.findById(detailId)).isPresent();
    assertThat(summaryRepository.findById(summaryId)).isPresent();

    TariffExemptionDto second = dto("Item1-Updated", "Model-B");
    SaveResult secondResult = service.saveAll(List.of(second), List.of(7), commonData);

    assertThat(secondResult.created()).isZero();
    assertThat(secondResult.updated()).isEqualTo(1);
    assertThat(repository.findById(detailId))
        .get()
        .extracting(TariffExemption::getItemName, TariffExemption::getModelName)
        .containsExactly("Item1-Updated", "Model-B");
    assertThat(summaryRepository.findById(summaryId)).isPresent();
  }

  @Test
  void saveAll_sameSummaryId_updatesUploadedRows() {
    TariffExemptionCommonData commonData = commonData();
    TariffExemptionSummaryId summaryId =
        new TariffExemptionSummaryId("2026", "001", "U001", "EQ-01");

    service.saveAll(List.of(dto("Item1", "Model-A")), List.of(7), commonData);
    service.saveAll(
        List.of(dto("Item1", "Model-A"), dto("Item2", "Model-B")), List.of(7, 8), commonData);

    assertThat(summaryRepository.findById(summaryId))
        .get()
        .extracting(TariffExemptionSummary::getUploadedRows)
        .isEqualTo(2);
  }

  private TariffExemptionCommonData commonData() {
    TariffExemptionCommonData commonData = new TariffExemptionCommonData();
    commonData.setComeYear("2026");
    commonData.setComeSequence("001");
    commonData.setUploadSequence("U001");
    commonData.setEquipCode("EQ-01");
    return commonData;
  }

  private TariffExemptionDto dto(String itemName, String modelName) {
    TariffExemptionDto dto = new TariffExemptionDto();
    dto.setSequenceNo(1);
    dto.setItemName(itemName);
    dto.setSpecification("Spec");
    dto.setModelName(modelName);
    dto.setHsCode("8481.80-2000");
    dto.setTariffRate(new BigDecimal("8.00"));
    dto.setUnitPrice(new BigDecimal("100.00"));
    dto.setQtyForManufacturing(10);
    dto.setQtyForRepair(5);
    dto.setAnnualImportEstimate(new BigDecimal("1000.00"));
    dto.setReviewResult("통과");
    dto.setAnnualExpectedQty(100);
    return dto;
  }
}
