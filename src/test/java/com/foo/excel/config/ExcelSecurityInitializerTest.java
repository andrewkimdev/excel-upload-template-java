package com.foo.excel.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.ApplicationArguments;

class ExcelSecurityInitializerTest {

  @Test
  void run_initializesPoiSecurityWithoutError() {
    ExcelSecurityInitializer initializer = new ExcelSecurityInitializer();
    ApplicationArguments args = Mockito.mock(ApplicationArguments.class);

    assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();
  }
}
