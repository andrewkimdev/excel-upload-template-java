package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.service.TemplateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TariffExemptionTemplateConfig {

    @Bean
    public TemplateDefinition<TariffExemptionDto> tariffExemptionTemplate(
            TariffExemptionService persistenceHandler,
            TariffExemptionDbUniquenessChecker dbUniquenessChecker) {
        return new TemplateDefinition<>(
                "tariff-exemption",
                TariffExemptionDto.class,
                new TariffExemptionImportConfig(),
                persistenceHandler,
                dbUniquenessChecker
        );
    }
}
