package com.foo.excel.templates.samples.aappcar.persistence.repository;

import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AAppcarItemRepository
    extends JpaRepository<AAppcarItem, AAppcarItemId> {}
