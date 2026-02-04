package com.foo.excel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExcelUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelUploadApplication.class, args);
    }
}
