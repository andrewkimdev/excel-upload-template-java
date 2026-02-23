package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.service.TemplateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TariffExemptionTemplateConfig {

    @Bean
    public TemplateDefinition<TariffExemptionDto, TariffExemptionCommonData> tariffExemptionTemplate(
            TariffExemptionService persistenceHandler) {
        return new TemplateDefinition<>(
                "tariff-exemption",
                TariffExemptionDto.class,
                TariffExemptionCommonData.class,
                new TariffExemptionImportConfig(),
                persistenceHandler,
                null
        );
    }
}
