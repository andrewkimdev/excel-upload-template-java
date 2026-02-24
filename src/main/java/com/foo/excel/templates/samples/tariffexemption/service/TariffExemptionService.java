package com.foo.excel.templates.samples.tariffexemption.service;

import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionCommonData;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionDto;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemption;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionId;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummary;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemptionSummaryId;
import com.foo.excel.templates.samples.tariffexemption.persistence.repository.TariffExemptionRepository;
import com.foo.excel.templates.samples.tariffexemption.persistence.repository.TariffExemptionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TariffExemptionService
        implements PersistenceHandler<TariffExemptionDto, TariffExemptionCommonData> {

    private static final int UPSERT_RETRY_LIMIT = 2;

    private final TariffExemptionRepository repository;
    private final TariffExemptionSummaryRepository summaryRepository;

    @Override
    @Transactional
    public SaveResult saveAll(List<TariffExemptionDto> dtos,
                              List<Integer> sourceRowNumbers,
                              TariffExemptionCommonData commonData) {
        int created = 0;
        int updated = 0;

        for (int i = 0; i < dtos.size(); i++) {
            boolean createdNow = upsertDetailWithRetry(dtos.get(i), sourceRowNumbers.get(i), commonData);
            if (createdNow) {
                created++;
            } else {
                updated++;
            }
        }

        upsertSummaryWithRetry(dtos.size(), commonData);

        return new SaveResult(created, updated);
    }

    private boolean upsertDetailWithRetry(TariffExemptionDto dto,
                                          Integer rowNumber,
                                          TariffExemptionCommonData commonData) {
        TariffExemptionId detailId = buildDetailId(commonData, rowNumber);
        int attempt = 0;
        while (attempt <= UPSERT_RETRY_LIMIT) {
            attempt++;
            try {
                Optional<TariffExemption> existing = repository.findById(detailId);

                if (existing.isPresent()) {
                    TariffExemption entity = existing.get();
                    updateEntityFromDto(entity, dto);
                    repository.saveAndFlush(entity);
                    return false;
                }

                TariffExemption newEntity = buildEntityFromDto(dto, commonData);
                newEntity.setId(detailId);
                repository.saveAndFlush(newEntity);
                return true;
            } catch (DataIntegrityViolationException e) {
                if (attempt > UPSERT_RETRY_LIMIT) {
                    throw new IllegalStateException("동시 저장 충돌로 상세 데이터 처리에 실패했습니다.");
                }
            }
        }

        throw new IllegalStateException("동시 저장 충돌로 상세 데이터 처리에 실패했습니다.");
    }

    private void upsertSummaryWithRetry(int rowCount, TariffExemptionCommonData commonData) {
        TariffExemptionSummaryId summaryId = buildSummaryId(commonData);
        int attempt = 0;
        while (attempt <= UPSERT_RETRY_LIMIT) {
            attempt++;
            try {
                Optional<TariffExemptionSummary> existing = summaryRepository.findById(summaryId);

                if (existing.isPresent()) {
                    TariffExemptionSummary entity = existing.get();
                    entity.setUploadedRows(rowCount);
                    applyAuditDefaults(entity, commonData);
                    summaryRepository.saveAndFlush(entity);
                    return;
                }

                TariffExemptionSummary newEntity = TariffExemptionSummary.builder()
                        .id(summaryId)
                        .uploadedRows(rowCount)
                        .build();
                applyAuditDefaults(newEntity, commonData);
                summaryRepository.saveAndFlush(newEntity);
                return;
            } catch (DataIntegrityViolationException e) {
                if (attempt > UPSERT_RETRY_LIMIT) {
                    throw new IllegalStateException("동시 저장 충돌로 요약 데이터 처리에 실패했습니다.");
                }
            }
        }

        throw new IllegalStateException("동시 저장 충돌로 요약 데이터 처리에 실패했습니다.");
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

    private TariffExemption buildEntityFromDto(TariffExemptionDto dto,
                                               TariffExemptionCommonData commonData) {
        TariffExemption entity = TariffExemption.builder()
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
        applyAuditDefaults(entity, commonData);
        return entity;
    }

    private TariffExemptionId buildDetailId(TariffExemptionCommonData commonData, Integer rowNumber) {
        return new TariffExemptionId(
                commonData.getComeYear(),
                commonData.getComeSequence(),
                commonData.getUploadSequence(),
                commonData.getEquipCode(),
                rowNumber
        );
    }

    private TariffExemptionSummaryId buildSummaryId(TariffExemptionCommonData commonData) {
        return new TariffExemptionSummaryId(
                commonData.getComeYear(),
                commonData.getComeSequence(),
                commonData.getUploadSequence(),
                commonData.getEquipCode()
        );
    }

    private void applyAuditDefaults(TariffExemption entity, TariffExemptionCommonData commonData) {
        entity.setApprovedYn(commonData.getApprovedYn());
        entity.setCreatedBy(commonData.getCreatedBy());
        entity.setCreatedAt(LocalDateTime.now());
    }

    private void applyAuditDefaults(TariffExemptionSummary entity,
                                    TariffExemptionCommonData commonData) {
        entity.setApprovedYn(commonData.getApprovedYn());
        entity.setCreatedBy(commonData.getCreatedBy());
        entity.setCreatedAt(LocalDateTime.now());
    }
}
