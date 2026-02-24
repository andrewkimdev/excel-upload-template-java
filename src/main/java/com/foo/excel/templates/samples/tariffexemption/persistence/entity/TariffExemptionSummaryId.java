package com.foo.excel.templates.samples.tariffexemption.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class TariffExemptionSummaryId implements Serializable {

    @Column(name = "come_year", nullable = false, length = 20)
    private String comeYear;

    @Column(name = "come_sequence", nullable = false, length = 50)
    private String comeSequence;

    @Column(name = "upload_sequence", nullable = false, length = 50)
    private String uploadSequence;

    @Column(name = "equip_code", nullable = false, length = 50)
    private String equipCode;
}
