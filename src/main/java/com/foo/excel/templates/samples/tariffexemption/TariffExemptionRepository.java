package com.foo.excel.templates.samples.tariffexemption;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffExemptionRepository extends JpaRepository<TariffExemption, TariffExemptionId> {

    boolean existsByItemNameAndSpecificationAndHsCode(
            String itemName,
            String specification,
            String hsCode);
}
