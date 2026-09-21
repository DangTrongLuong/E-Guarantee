package com.example.ecommerce.repository;

import com.example.ecommerce.entity.GuaranteeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GuaranteeRequestRepository extends JpaRepository<GuaranteeRequest, String>, JpaSpecificationExecutor<GuaranteeRequest> {
    long count();
    boolean existsByCustomer_Cif(String cif);
}
