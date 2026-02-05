package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.config.ExcelImportConfig;

import java.util.Set;

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
    public Set<String> getSkipColumns() {
        return Set.of("A", "G", "K", "M", "P");
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
