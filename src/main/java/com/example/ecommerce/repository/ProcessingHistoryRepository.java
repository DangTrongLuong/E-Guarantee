package com.example.ecommerce.repository;

import com.example.ecommerce.entity.ProcessingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingHistoryRepository extends JpaRepository<ProcessingHistory, Long> {
    List<ProcessingHistory> findByGuaranteeRequest_IdOrderByTimestampAsc(String guaranteeId);

    void deleteByGuaranteeRequest_Id(String guaranteeId);
}