package com.foo.excel.templates.samples.aappcar.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.io.Serial;
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
public class AAppcarEquipId implements Serializable {

  @Serial
  private static final long serialVersionUID = 123L;

  @Size(max = 35)
  @NotNull
  @Column(name = "company_id", nullable = false, length = 35)
  private String companyId;

  @Size(max = 35)
  @NotNull
  @Column(name = "custom_id", nullable = false, length = 35)
  private String customId;
  
  @Column(name = "come_year", nullable = false, length = 4)
  private String comeYear;

  @NotNull
  @Column(name = "come_order", nullable = false)
  private Integer comeOrder;

  @NotNull
  @Column(name = "upload_seq", nullable = false)
  private Integer uploadSeq;

  @Column(name = "equip_code", nullable = false, length = 50)
  private String equipCode;

}
