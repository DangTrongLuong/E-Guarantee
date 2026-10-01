package com.example.ecommerce.repository;

import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.enums.GuaranteeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GuaranteeRequestRepository extends JpaRepository<GuaranteeRequest, String>, JpaSpecificationExecutor<GuaranteeRequest> {
    long count();
    boolean existsByCustomer_Cif(String cif);
    long countByStatus(GuaranteeStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from GuaranteeRequest g where g.id = :id")
    java.util.Optional<GuaranteeRequest> findForSigning(@org.springframework.data.repository.query.Param("id") String id);

    java.util.List<GuaranteeRequest> findBySignatureStatus(com.example.ecommerce.enums.SignatureStatus status);
}
