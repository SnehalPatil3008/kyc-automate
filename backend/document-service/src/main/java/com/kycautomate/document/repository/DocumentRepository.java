package com.kycautomate.document.repository;

import com.kycautomate.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByApplicationIdOrderByCreatedAtAsc(String applicationId);
    List<Document> findByApplicationIdAndActiveTrueOrderByCreatedAtAsc(String applicationId);
}
