package com.foo.excel.templates.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class AAppcarItemEmbeddedIdMappingTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void tariffItemTable_usesCompositePrimaryKeyColumns_withoutSurrogateIdColumn() {
    List<String> columns = columnsOf("A_APPCAR_ITEM");

    assertThat(columns).doesNotContain("ID");
    assertThat(columns)
        .contains("COME_YEAR", "COME_ORDER", "UPLOAD_SEQ", "EQUIP_CODE", "ROW_NUMBER");
  }

  @Test
  void tariffEquipTable_usesCompositePrimaryKeyColumns_withoutSurrogateIdColumn() {
    List<String> columns = columnsOf("A_APPCAR_EQUIP");

    assertThat(columns).doesNotContain("ID");
    assertThat(columns)
        .contains(
            "COMPANY_ID", "CUSTOM_ID", "COME_YEAR", "COME_ORDER", "UPLOAD_SEQ", "EQUIP_CODE");
  }

  private List<String> columnsOf(String tableName) {
    return jdbcTemplate
        .queryForList(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
            String.class,
            tableName)
        .stream()
        .map(String::toUpperCase)
        .collect(Collectors.toList());
  }
}
