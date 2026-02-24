package com.foo.excel.templates.samples.tariffexemption.persistence.repository;

import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemption;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffExemptionRepository
    extends JpaRepository<TariffExemption, TariffExemptionId> {

  boolean existsByItemNameAndSpecificationAndHsCode(
      String itemName, String specification, String hsCode);
}
