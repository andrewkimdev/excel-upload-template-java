package com.foo.excel.templates.samples.tariffexemption;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tariff_exemption")
public class TariffExemption {

    @EmbeddedId
    private TariffExemptionId id;

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

    @Column(name = "approved_yn", nullable = false, length = 1)
    private String approvedYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;
}
