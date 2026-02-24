package com.foo.excel.templates.samples.tariffexemption.persistence.repository;

import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummary;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummaryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffExemptionSummaryRepository
    extends JpaRepository<TariffExemptionSummary, TariffExemptionSummaryId> {}
