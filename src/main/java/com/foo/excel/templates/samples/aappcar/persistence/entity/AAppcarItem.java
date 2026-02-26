package com.foo.excel.templates.samples.aappcar.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@Table(name = "a_appcar_item")
public class AAppcarItem {

  @EmbeddedId private AAppcarItemId id;

  @Size(max = 35)
  @Column(name = "goods_code", length = 35)
  private String goodsCode;

  @Size(max = 100)
  @Column(name = "goods_des", length = 100)
  private String goodsDes;

  @Size(max = 100)
  @Column(name = "spec", length = 100)
  private String spec;

  @Size(max = 100)
  @Column(name = "model_des", length = 100)
  private String modelDes;

  @Size(max = 12)
  @Column(name = "hsno", length = 12)
  private String hsno;

  @Column(name = "tax_rate", precision = 10, scale = 4)
  private BigDecimal taxRate;

  @Column(name = "unitprice", precision = 15, scale = 4)
  private BigDecimal unitprice;

  @Size(max = 3)
  @Column(name = "unitprice_unit", length = 3)
  private String unitpriceUnit;

  @Column(name = "prod_qty")
  private Integer prodQty;

  @Column(name = "repair_qty")
  private Integer repairQty;

  @Column(name = "import_amt", precision = 15, scale = 4)
  private BigDecimal importAmt;

  @Size(max = 3)
  @Column(name = "exch", length = 3)
  private String exch;

  @Column(name = "import_qty")
  private Integer importQty;

  @Size(max = 1)
  @Column(name = "approval_yn", nullable = false, length = 1)
  private String approvalYn;

  @Column(name = "old_qty")
  private Integer oldQty;

  @Column(name = "old_amt", precision = 15, scale = 4)
  private BigDecimal oldAmt;
}
