package com.foo.excel.templates.samples.aappcar.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class AAppcarItemId implements Serializable {

  @Column(name = "come_year", nullable = false, length = 20)
  private String comeYear;

  @Column(name = "come_order", nullable = false, length = 50)
  private String comeOrder;

  @Column(name = "upload_seq", nullable = false, length = 50)
  private String uploadSeq;

  @Column(name = "equip_code", nullable = false, length = 50)
  private String equipCode;

  @Column(name = "row_number", nullable = false)
  private Integer rowNumber;
}
