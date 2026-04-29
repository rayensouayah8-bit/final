package com.smartvoyage.messaging.repository;

import com.smartvoyage.messaging.model.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
}
