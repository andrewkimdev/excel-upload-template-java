package com.foo.excel.templates.samples.tariffexemption.config;

import com.foo.excel.config.ExcelImportConfig;

public class TariffExemptionImportConfig implements ExcelImportConfig {

    @Override
    public int getHeaderRow() {
        return 4;
    }

    @Override
    public int getDataStartRow() {
        return 7;
    }

    @Override
    public String getFooterMarker() {
        return "※";
    }

    @Override
    public String[] getNaturalKeyFields() {
        return new String[]{"itemName", "specification", "hsCode"};
    }
}
