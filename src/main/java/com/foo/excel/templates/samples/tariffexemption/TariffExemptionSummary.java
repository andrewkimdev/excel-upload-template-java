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

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tariff_exemption_summary")
public class TariffExemptionSummary {

    @EmbeddedId
    private TariffExemptionSummaryId id;

    @Column(name = "uploaded_rows", nullable = false)
    private Integer uploadedRows;

    @Column(name = "approved_yn", nullable = false, length = 1)
    private String approvedYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;
}
