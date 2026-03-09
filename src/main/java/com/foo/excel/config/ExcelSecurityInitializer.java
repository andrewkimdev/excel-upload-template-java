package com.foo.excel.config;

import com.foo.excel.util.SecureExcelUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExcelSecurityInitializer implements ApplicationRunner {

  @Override
  public void run(ApplicationArguments args) {
    SecureExcelUtils.initializePoiSecurity();
  }
}
