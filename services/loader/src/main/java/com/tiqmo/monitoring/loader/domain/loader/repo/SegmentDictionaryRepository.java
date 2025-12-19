// src/main/java/com/tiqmo/monitoring/loader/domain/signals/repo/SegmentDictionaryRepository.java
package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.SegmentDictionary;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface SegmentDictionaryRepository extends JpaRepository<SegmentDictionary, Long> {
    List<SegmentDictionary> findByLoader(String loader, Sort sort);
}
