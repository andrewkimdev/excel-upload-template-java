package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.service.TemplateDefinition;
import com.foo.excel.templates.TemplateTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TariffExemptionTemplateConfig {

    @Bean
    public TemplateDefinition<TariffExemptionDto, TariffExemptionCommonData> tariffExemptionTemplate(
            TariffExemptionService persistenceHandler) {
        return new TemplateDefinition<>(
                TemplateTypes.TARIFF_EXEMPTION,
                TariffExemptionDto.class,
                TariffExemptionCommonData.class,
                new TariffExemptionImportConfig(),
                persistenceHandler,
                null
        );
    }
}
