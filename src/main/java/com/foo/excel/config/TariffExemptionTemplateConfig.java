package com.foo.excel.config;

import com.foo.excel.dto.TariffExemptionDto;
import com.foo.excel.service.TariffExemptionDbUniquenessChecker;
import com.foo.excel.service.TariffExemptionService;
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
