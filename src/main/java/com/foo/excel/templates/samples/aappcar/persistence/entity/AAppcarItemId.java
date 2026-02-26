package com.foo.excel.templates.samples.aappcar.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  @Size(max = 35)
  @NotNull
  @Column(name = "company_id", nullable = false, length = 35)
  private String companyId;

  @Size(max = 35)
  @NotNull
  @Column(name = "custom_id", nullable = false, length = 35)
  private String customId;

  @Column(name = "come_year", nullable = false, length = 20)
  private String comeYear;

  @Column(name = "come_order", nullable = false, length = 50)
  private String comeOrder;

  @Column(name = "upload_seq", nullable = false, length = 50)
  private String uploadSeq;

  @Column(name = "equip_code", nullable = false, length = 50)
  private String equipCode;

  @NotNull
  @Column(name = "goods_seq_no", nullable = false)
  private Integer goodsSeqNo;
}
