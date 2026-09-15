package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.request.CustomerUpdateRequest;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.CustomerMapper;
import com.example.ecommerce.repository.CustomerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CustomerService {
    CustomerRepository customerRepository;
    CustomerMapper customerMapper;

    public CustomerResponse createCustomer(CustomerCreationRequest customerCreationRequest){
        if(customerRepository.existsByTaxCode(customerCreationRequest.getTaxCode())){
            throw new ConflictException("Mã số thuế đã tồn tại, vui lòng thử lại !");
        }

        Customer customer = customerMapper.createCustomer(customerCreationRequest);
        return customerMapper.customerResponse(customerRepository.save(customer));
    }

    public List<CustomerResponse> getAllCustomer(){
        return customerRepository.findAll().stream()
                .map(customerMapper::customerResponse)
                .toList();
    }

    public CustomerResponse getCustomerByCif(String cif){
        Customer customer = customerRepository.findById(cif)
                .orElseThrow(() -> new ResourceNotFoundException("Cif không tồn tại! Vui lòng thử lại."));

        return customerMapper.customerResponse(customer);
    }

    public CustomerResponse updateCustomer(String cif, CustomerUpdateRequest customerUpdateRequest){
        Customer customer = customerRepository.findById(cif)
                .orElseThrow(() -> new ResourceNotFoundException("Cif không tồn tại! Vui lòng thử lại."));

        if(!customer.getTaxCode().equals(customerUpdateRequest.getTaxCode())
            && customerRepository.existsByTaxCode(customerUpdateRequest.getTaxCode())){
            throw new ConflictException("Mã số thuế đã tồn tại, vui lòng thử lại !");
        }

        customerMapper.updateCustomer(customerUpdateRequest, customer);

        return customerMapper.customerResponse(customerRepository.save(customer));

    }

    public void deleteCustomer(String cif){
        Customer customer = customerRepository.findById(cif)
                .orElseThrow(() -> new ResourceNotFoundException("Cif không tồn tại! Vui lòng thử lại."));

        customerRepository.delete(customer);
    }
}
