package com.example.ecommerce.repository;

import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.enums.GuaranteeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GuaranteeRequestRepository extends JpaRepository<GuaranteeRequest, String>, JpaSpecificationExecutor<GuaranteeRequest> {
    long count();
    long countByStatus(GuaranteeStatus status);
}
