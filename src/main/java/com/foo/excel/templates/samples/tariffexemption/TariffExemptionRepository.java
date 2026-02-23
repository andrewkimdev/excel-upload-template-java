package com.foo.excel.templates.samples.tariffexemption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TariffExemptionRepository extends JpaRepository<TariffExemption, Long> {

    Optional<TariffExemption> findByComeYearAndComeSequenceAndUploadSequenceAndEquipCodeAndRowNumber(
            String comeYear,
            String comeSequence,
            String uploadSequence,
            String equipCode,
            Integer rowNumber);

    boolean existsByItemNameAndSpecificationAndHsCode(
            String itemName,
            String specification,
            String hsCode);
}
