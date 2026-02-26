package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.config.ExcelImportConfig;

public class AAppcarItemImportConfig implements ExcelImportConfig {

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

}
