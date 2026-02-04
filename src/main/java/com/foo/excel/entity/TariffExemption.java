package com.foo.excel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tariff_exemption", uniqueConstraints =
    @UniqueConstraint(columnNames = {"item_name", "specification", "hs_code"})
)
public class TariffExemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "specification", length = 200)
    private String specification;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "hs_code", length = 20)
    private String hsCode;

    @Column(name = "tariff_rate", precision = 5, scale = 2)
    private BigDecimal tariffRate;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "qty_for_manufacturing")
    private Integer qtyForManufacturing;

    @Column(name = "qty_for_repair")
    private Integer qtyForRepair;

    @Column(name = "annual_import_estimate", precision = 15, scale = 2)
    private BigDecimal annualImportEstimate;

    @Column(name = "review_result", length = 100)
    private String reviewResult;

    @Column(name = "annual_expected_qty")
    private Integer annualExpectedQty;
}
