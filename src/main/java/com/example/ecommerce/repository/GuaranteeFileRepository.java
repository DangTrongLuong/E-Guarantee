package com.example.ecommerce.repository;

import com.example.ecommerce.entity.GuaranteeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuaranteeFileRepository extends JpaRepository<GuaranteeFile, Long> {

    List<GuaranteeFile> findByGuaranteeId(String guaranteeId);

    Optional<GuaranteeFile> findByPublicId(String publicId);

    void deleteByPublicId(String publicId);
}
