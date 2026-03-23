package com.foo.excel.imports.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.ExcelImportApplication;
import com.foo.excel.service.contract.PersistenceHandler.SaveResult;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = ExcelImportApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AAppcarItemEmbeddedIdPersistenceTest {

  @Autowired private AAppcarItemService service;

  @Autowired private AAppcarItemRepository itemRepository;

  @Autowired private AAppcarEquipRepository equipRepository;

  @Test
  void saveAll_sameItemId_createsThenUpdates_withFindByIdRoundTrip() {
    AAppcarItemMetadata metadata = metadata();

    AAppcarItemDto first = dto("Item1", "Model-A");
    SaveResult firstResult = service.saveAll(List.of(first), List.of(7), metadata);

    AAppcarItemId itemId =
        new AAppcarItemId("COMPANY01", "CUSTOM01", "2026", "001", "1", "EQ-01", 7);
    AAppcarEquipId equipId =
        new AAppcarEquipId("COMPANY01", "CUSTOM01", "2026", 1, 1, "EQ-01");

    assertThat(firstResult.created()).isEqualTo(1);
    assertThat(firstResult.updated()).isZero();
    assertThat(itemRepository.findById(itemId)).isPresent();
    assertThat(equipRepository.findById(equipId)).isPresent();

    AAppcarItemDto second = dto("Item1-Updated", "Model-B");
    SaveResult secondResult = service.saveAll(List.of(second), List.of(7), metadata);

    assertThat(secondResult.created()).isZero();
    assertThat(secondResult.updated()).isEqualTo(1);
    assertThat(itemRepository.findById(itemId))
        .get()
        .extracting(AAppcarItem::getGoodsDes, AAppcarItem::getModelDes)
        .containsExactly("Item1-Updated", "Model-B");
    assertThat(equipRepository.findById(equipId)).isPresent();
  }

  @Test
  void saveAll_sameEquipId_updatesEquipFields() {
    AAppcarItemMetadata metadata = metadata();
    AAppcarEquipId equipId =
        new AAppcarEquipId("COMPANY01", "CUSTOM01", "2026", 1, 1, "EQ-01");

    service.saveAll(List.of(dto("Item1", "Model-A")), List.of(7), metadata);
    metadata.setEquipMean("설비B");
    metadata.setSpec("규격B");
    metadata.setHsno("999999999999");
    metadata.setTaxRate(new BigDecimal("9.50"));
    metadata.setApprovalYn("Y");
    service.saveAll(
        List.of(dto("Item1", "Model-A"), dto("Item2", "Model-B")), List.of(7, 8), metadata);

    assertThat(equipRepository.findById(equipId))
        .get()
        .extracting(
            AAppcarEquip::getEquipMean,
            AAppcarEquip::getSpec,
            AAppcarEquip::getHsno,
            AAppcarEquip::getTaxRate,
            AAppcarEquip::getApprovalYn,
            AAppcarEquip::getApprovalDate)
        .containsExactly(
            "설비B", "규격B", "999999999999", new BigDecimal("9.50"), "Y", LocalDate.now());
  }

  @Test
  void saveAll_mixedExistingAndNewRows_updatesAndInsertsInSingleBatch() {
    AAppcarItemMetadata metadata = metadata();
    AAppcarItemId existingItemId =
        new AAppcarItemId("COMPANY01", "CUSTOM01", "2026", "001", "1", "EQ-01", 7);
    AAppcarItemId newItemId =
        new AAppcarItemId("COMPANY01", "CUSTOM01", "2026", "001", "1", "EQ-01", 8);

    service.saveAll(List.of(dto("Item1", "Model-A")), List.of(7), metadata);

    SaveResult result =
        service.saveAll(
            List.of(dto("Item1-Updated", "Model-B"), dto("Item2", "Model-C")), List.of(7, 8), metadata);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(itemRepository.findById(existingItemId))
        .get()
        .extracting(AAppcarItem::getGoodsDes, AAppcarItem::getModelDes)
        .containsExactly("Item1-Updated", "Model-B");
    assertThat(itemRepository.findById(newItemId))
        .get()
        .extracting(AAppcarItem::getGoodsDes, AAppcarItem::getModelDes)
        .containsExactly("Item2", "Model-C");
  }

  private AAppcarItemMetadata metadata() {
    AAppcarItemMetadata metadata = new AAppcarItemMetadata();
    metadata.setComeYear("2026");
    metadata.setComeOrder("001");
    metadata.setUploadSeq("1");
    metadata.setEquipCode("EQ-01");
    metadata.setEquipMean("설비A");
    metadata.setHsno("8481802000");
    metadata.setSpec("규격A");
    metadata.setTaxRate(new BigDecimal("8.50"));
    metadata.setCompanyId("COMPANY01");
    metadata.setCustomId("CUSTOM01");
    return metadata;
  }

  private AAppcarItemDto dto(String itemName, String modelName) {
    AAppcarItemDto dto = new AAppcarItemDto();
    dto.setGoodsSeqNo(1);
    dto.setGoodsDes(itemName);
    dto.setSpec("Spec");
    dto.setModelDes(modelName);
    dto.setHsno("8481.80-2000");
    dto.setTaxRate(new BigDecimal("8.00"));
    dto.setUnitprice(new BigDecimal("100.00"));
    dto.setProdQty(10);
    dto.setRepairQty(5);
    dto.setImportAmt(new BigDecimal("1000.00"));
    dto.setApprovalYn("통과");
    dto.setImportQty(100);
    return dto;
  }
}
