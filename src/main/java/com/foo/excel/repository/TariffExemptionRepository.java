package com.foo.excel.repository;

import com.foo.excel.entity.TariffExemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TariffExemptionRepository extends JpaRepository<TariffExemption, Long> {

    Optional<TariffExemption> findByItemNameAndSpecificationAndHsCode(
            String itemName, String specification, String hsCode);

    boolean existsByItemNameAndSpecificationAndHsCode(
            String itemName, String specification, String hsCode);
}
