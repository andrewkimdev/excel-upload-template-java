package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.service.PersistenceHandler;
import com.foo.excel.service.UploadCommonData;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TariffExemptionService implements PersistenceHandler<TariffExemptionDto> {

    private static final int UPSERT_RETRY_LIMIT = 2;

    private final TariffExemptionRepository repository;
    private final TariffExemptionSummaryRepository summaryRepository;

    @Override
    @Transactional
    public SaveResult saveAll(List<TariffExemptionDto> dtos,
                              List<Integer> sourceRowNumbers,
                              UploadCommonData commonData) {
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
                                          UploadCommonData commonData) {
        int attempt = 0;
        while (attempt <= UPSERT_RETRY_LIMIT) {
            attempt++;
            try {
                Optional<TariffExemption> existing = repository
                        .findByComeYearAndComeSequenceAndUploadSequenceAndEquipCodeAndRowNumber(
                                commonData.getComeYear(),
                                commonData.getComeSequence(),
                                commonData.getUploadSequence(),
                                commonData.getEquipCode(),
                                rowNumber);

                if (existing.isPresent()) {
                    TariffExemption entity = existing.get();
                    updateEntityFromDto(entity, dto);
                    repository.saveAndFlush(entity);
                    return false;
                }

                TariffExemption newEntity = buildEntityFromDto(dto, rowNumber, commonData);
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

    private void upsertSummaryWithRetry(int rowCount, UploadCommonData commonData) {
        int attempt = 0;
        while (attempt <= UPSERT_RETRY_LIMIT) {
            attempt++;
            try {
                Optional<TariffExemptionSummary> existing = summaryRepository
                        .findByComeYearAndComeSequenceAndUploadSequenceAndEquipCode(
                                commonData.getComeYear(),
                                commonData.getComeSequence(),
                                commonData.getUploadSequence(),
                                commonData.getEquipCode());

                if (existing.isPresent()) {
                    TariffExemptionSummary entity = existing.get();
                    entity.setUploadedRows(rowCount);
                    applyAuditDefaults(entity, commonData);
                    summaryRepository.saveAndFlush(entity);
                    return;
                }

                TariffExemptionSummary newEntity = TariffExemptionSummary.builder()
                        .comeYear(commonData.getComeYear())
                        .comeSequence(commonData.getComeSequence())
                        .uploadSequence(commonData.getUploadSequence())
                        .equipCode(commonData.getEquipCode())
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
                                               Integer rowNumber,
                                               UploadCommonData commonData) {
        TariffExemption entity = TariffExemption.builder()
                .comeYear(commonData.getComeYear())
                .comeSequence(commonData.getComeSequence())
                .uploadSequence(commonData.getUploadSequence())
                .equipCode(commonData.getEquipCode())
                .rowNumber(rowNumber)
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

    private void applyAuditDefaults(TariffExemption entity, UploadCommonData commonData) {
        entity.setApprovedYn(commonData.getApprovedYn());
        entity.setCreatedBy(commonData.getCreatedBy());
        entity.setCreatedAt(LocalDateTime.now());
    }

    private void applyAuditDefaults(TariffExemptionSummary entity, UploadCommonData commonData) {
        entity.setApprovedYn(commonData.getApprovedYn());
        entity.setCreatedBy(commonData.getCreatedBy());
        entity.setCreatedAt(LocalDateTime.now());
    }
}
