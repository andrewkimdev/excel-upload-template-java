package com.foo.excel.templates.samples.tariffexemption.config;

import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionCommonData;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionDto;
import com.foo.excel.templates.samples.tariffexemption.service.TariffExemptionService;
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
