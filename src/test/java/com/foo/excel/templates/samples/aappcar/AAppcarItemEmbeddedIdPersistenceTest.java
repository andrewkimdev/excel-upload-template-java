package com.foo.excel.templates.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.ExcelUploadApplication;
import com.foo.excel.service.contract.PersistenceHandler.SaveResult;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = ExcelUploadApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AAppcarItemEmbeddedIdPersistenceTest {

  @Autowired private AAppcarItemService service;

  @Autowired private AAppcarItemRepository itemRepository;

  @Autowired private AAppcarEquipRepository equipRepository;

  @Test
  void saveAll_sameItemId_createsThenUpdates_withFindByIdRoundTrip() {
    AAppcarItemCommonData commonData = commonData();

    AAppcarItemDto first = dto("Item1", "Model-A");
    SaveResult firstResult = service.saveAll(List.of(first), List.of(7), commonData);

    AAppcarItemId itemId = new AAppcarItemId("2026", "001", "1", "EQ-01", 7);
    AAppcarEquipId equipId =
        new AAppcarEquipId("COMPANY01", "CUSTOM01", "2026", 1, 1, "EQ-01");

    assertThat(firstResult.created()).isEqualTo(1);
    assertThat(firstResult.updated()).isZero();
    assertThat(itemRepository.findById(itemId)).isPresent();
    assertThat(equipRepository.findById(equipId)).isPresent();

    AAppcarItemDto second = dto("Item1-Updated", "Model-B");
    SaveResult secondResult = service.saveAll(List.of(second), List.of(7), commonData);

    assertThat(secondResult.created()).isZero();
    assertThat(secondResult.updated()).isEqualTo(1);
    assertThat(itemRepository.findById(itemId))
        .get()
        .extracting(AAppcarItem::getItemName, AAppcarItem::getModelName)
        .containsExactly("Item1-Updated", "Model-B");
    assertThat(equipRepository.findById(equipId)).isPresent();
  }

  @Test
  void saveAll_sameEquipId_updatesUploadedRows() {
    AAppcarItemCommonData commonData = commonData();
    AAppcarEquipId equipId =
        new AAppcarEquipId("COMPANY01", "CUSTOM01", "2026", 1, 1, "EQ-01");

    service.saveAll(List.of(dto("Item1", "Model-A")), List.of(7), commonData);
    service.saveAll(
        List.of(dto("Item1", "Model-A"), dto("Item2", "Model-B")), List.of(7, 8), commonData);

    assertThat(equipRepository.findById(equipId))
        .get()
        .extracting(AAppcarEquip::getUploadedRows)
        .isEqualTo(2);
  }

  private AAppcarItemCommonData commonData() {
    AAppcarItemCommonData commonData = new AAppcarItemCommonData();
    commonData.setComeYear("2026");
    commonData.setComeOrder("001");
    commonData.setUploadSeq("1");
    commonData.setEquipCode("EQ-01");
    commonData.setCompanyId("COMPANY01");
    commonData.setCustomId("CUSTOM01");
    return commonData;
  }

  private AAppcarItemDto dto(String itemName, String modelName) {
    AAppcarItemDto dto = new AAppcarItemDto();
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
