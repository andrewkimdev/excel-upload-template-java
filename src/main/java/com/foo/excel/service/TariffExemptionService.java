package com.foo.excel.service;

import com.foo.excel.dto.TariffExemptionDto;
import com.foo.excel.entity.TariffExemption;
import com.foo.excel.repository.TariffExemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TariffExemptionService {

    private final TariffExemptionRepository repository;

    public record SaveResult(int created, int updated) {}

    @Transactional
    public SaveResult saveAll(List<TariffExemptionDto> dtos) {
        int created = 0;
        int updated = 0;
        List<TariffExemption> toSave = new ArrayList<>();

        for (TariffExemptionDto dto : dtos) {
            Optional<TariffExemption> existing = repository.findByItemNameAndSpecificationAndHsCode(
                    dto.getItemName(), dto.getSpecification(), dto.getHsCode());

            if (existing.isPresent()) {
                TariffExemption entity = existing.get();
                updateEntityFromDto(entity, dto);
                toSave.add(entity);
                updated++;
            } else {
                toSave.add(buildEntityFromDto(dto));
                created++;
            }
        }

        repository.saveAll(toSave);
        return new SaveResult(created, updated);
    }

    private void updateEntityFromDto(TariffExemption entity, TariffExemptionDto dto) {
        entity.setSequenceNo(dto.getSequenceNo());
        entity.setItemName(dto.getItemName());
        entity.setSpecification(dto.getSpecification());
        entity.setModelName(dto.getModelName());
        entity.setHsCode(dto.getHsCode());
        entity.setTariffRate(dto.getTariffRate());
        entity.setUnitPrice(dto.getUnitPrice());
        entity.setQtyForManufacturing(dto.getQtyForManufacturing());
        entity.setQtyForRepair(dto.getQtyForRepair());
        entity.setAnnualImportEstimate(dto.getAnnualImportEstimate());
        entity.setReviewResult(dto.getReviewResult());
        entity.setAnnualExpectedQty(dto.getAnnualExpectedQty());
    }

    private TariffExemption buildEntityFromDto(TariffExemptionDto dto) {
        return TariffExemption.builder()
                .sequenceNo(dto.getSequenceNo())
                .itemName(dto.getItemName())
                .specification(dto.getSpecification())
                .modelName(dto.getModelName())
                .hsCode(dto.getHsCode())
                .tariffRate(dto.getTariffRate())
                .unitPrice(dto.getUnitPrice())
                .qtyForManufacturing(dto.getQtyForManufacturing())
                .qtyForRepair(dto.getQtyForRepair())
                .annualImportEstimate(dto.getAnnualImportEstimate())
                .reviewResult(dto.getReviewResult())
                .annualExpectedQty(dto.getAnnualExpectedQty())
                .build();
    }
}
