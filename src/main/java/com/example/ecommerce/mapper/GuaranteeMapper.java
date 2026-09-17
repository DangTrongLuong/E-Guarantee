package com.example.ecommerce.mapper;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.dto.response.ProcessingHistoryResponse;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.entity.ProcessingHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface GuaranteeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    GuaranteeRequest toEntity(GuaranteeCreationRequest request);

    @Mapping(target = "customerCif", source = "customer.cif")
    @Mapping(target = "customerName", source = "customer.customerName")
    @Mapping(target = "taxCode", source = "customer.taxCode")
    @Mapping(target = "customerAddress", source = "customer.address")
    @Mapping(target = "createdBy", source = "createdBy.username")
    @Mapping(target = "createdByFullName", source = "createdBy.fullName")
    @Mapping(target = "updatedBy", source = "updatedBy.username")
    @Mapping(target = "updatedByFullName", source = "updatedBy.fullName")
    @Mapping(target = "guaranteeDays", ignore = true)
    GuaranteeResponse toResponse(GuaranteeRequest entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    void updateEntityFromRequest(
            GuaranteeCreationRequest request,
            @MappingTarget GuaranteeRequest entity
    );

    @Mapping(target = "guaranteeId", ignore = true)
    @Mapping(target = "performedBy", ignore = true)
    @Mapping(target = "performedByFullName", ignore = true)
    ProcessingHistoryResponse toHistoryResponse(ProcessingHistory entity);
}