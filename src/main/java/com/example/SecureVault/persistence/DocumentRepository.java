package com.example.SecureVault.persistence;

import com.example.SecureVault.entity.Document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Returns a page of documents owned by the given user.
     */
    Page<Document> findByOwnerId(Long ownerId, Pageable pageable);
}
