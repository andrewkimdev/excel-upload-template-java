package com.foo.excel.templates.samples.aappcar.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "a_appcar_equip")
public class AAppcarEquip {

  @EmbeddedId private AAppcarEquipId id;

  @Size(max = 70)
  @Column(name = "equip_mean", length = 70)
  private String equipMean;

  @Size(max = 12)
  @Column(name = "hsno", length = 12)
  private String hsno;

  @Size(max = 70)
  @Column(name = "spec", length = 70)
  private String spec;

  @Column(name = "tax_rate", precision = 16, scale = 2)
  private BigDecimal taxRate;

  @Size(max = 100)
  @Column(name = "file_path", length = 100)
  private String filePath;

  @Size(max = 1)
  @Column(name = "approval_yn", length = 1)
  private String approvalYn;

  @Column(name = "approval_date")
  private LocalDate approvalDate;
}
