package com.foo.excel.templates.samples.aappcar.persistence.repository;

import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AAppcarEquipRepository
    extends JpaRepository<AAppcarEquip, AAppcarEquipId> {}
