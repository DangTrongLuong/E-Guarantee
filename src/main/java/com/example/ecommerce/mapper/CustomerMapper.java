package com.example.ecommerce.mapper;


import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.request.CustomerUpdateRequest;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    Customer createCustomer(CustomerCreationRequest customerCreationRequest);

    CustomerResponse customerResponse(Customer customer);

    void updateCustomer(CustomerUpdateRequest customerUpdateRequest, @MappingTarget Customer customer);
}
