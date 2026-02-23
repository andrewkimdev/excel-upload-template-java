package com.foo.excel.templates.samples.tariffexemption;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "tariff_exemption_summary", uniqueConstraints =
    @UniqueConstraint(columnNames = {
            "come_year", "come_sequence", "upload_sequence", "equip_code"
    })
)
public class TariffExemptionSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "come_year", nullable = false, length = 20)
    private String comeYear;

    @Column(name = "come_sequence", nullable = false, length = 50)
    private String comeSequence;

    @Column(name = "upload_sequence", nullable = false, length = 50)
    private String uploadSequence;

    @Column(name = "equip_code", nullable = false, length = 50)
    private String equipCode;

    @Column(name = "uploaded_rows", nullable = false)
    private Integer uploadedRows;

    @Column(name = "approved_yn", nullable = false, length = 1)
    private String approvedYn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;
}
