package com.foo.excel.templates.samples.tariffexemption;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TariffExemptionEmbeddedIdMappingTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tariffDetailTable_usesCompositePrimaryKeyColumns_withoutSurrogateIdColumn() {
        List<String> columns = columnsOf("TARIFF_EXEMPTION");

        assertThat(columns).doesNotContain("ID");
        assertThat(columns).contains(
                "COME_YEAR",
                "COME_SEQUENCE",
                "UPLOAD_SEQUENCE",
                "EQUIP_CODE",
                "ROW_NUMBER"
        );
    }

    @Test
    void tariffSummaryTable_usesCompositePrimaryKeyColumns_withoutSurrogateIdColumn() {
        List<String> columns = columnsOf("TARIFF_EXEMPTION_SUMMARY");

        assertThat(columns).doesNotContain("ID");
        assertThat(columns).contains(
                "COME_YEAR",
                "COME_SEQUENCE",
                "UPLOAD_SEQUENCE",
                "EQUIP_CODE"
        );
    }

    private List<String> columnsOf(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class,
                tableName
        ).stream().map(String::toUpperCase).collect(Collectors.toList());
    }
}
