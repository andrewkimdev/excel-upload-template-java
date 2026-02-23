package com.foo.excel.templates.samples.tariffexemption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TariffExemptionSummaryRepository extends JpaRepository<TariffExemptionSummary, Long> {

    Optional<TariffExemptionSummary> findByComeYearAndComeSequenceAndUploadSequenceAndEquipCode(
            String comeYear,
            String comeSequence,
            String uploadSequence,
            String equipCode);
}
