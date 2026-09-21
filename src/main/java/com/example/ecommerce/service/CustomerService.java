package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.request.CustomerUpdateRequest;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.CustomerMapper;
import com.example.ecommerce.repository.CustomerRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
        if (customerRepository.existsById(customerCreationRequest.getCif())) {
            throw new ConflictException("Mã CIF " + customerCreationRequest.getCif() + " đã tồn tại trong hệ thống, vui lòng kiểm tra lại!");
        }

        if (customerCreationRequest.getTaxCode() != null && !customerCreationRequest.getTaxCode().isBlank()) {
            if (customerRepository.existsByTaxCode(customerCreationRequest.getTaxCode())) {
                throw new ConflictException("Mã số thuế đã tồn tại, vui lòng thử lại !");
            }
        }

        Customer customer = customerMapper.createCustomer(customerCreationRequest);
        return customerMapper.customerResponse(customerRepository.save(customer));
    }

    public PageResponse<CustomerResponse> getAllCustomer(int page, int size){
        if (page < 0) {
            throw new BadRequestException("Tham số page phải lớn hơn hoặc bằng 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Tham số size phải lớn hơn 0");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Customer> customerPage = customerRepository.findAll(pageable);
        Page<CustomerResponse> responsePage = customerPage.map(customerMapper::customerResponse);

        return PageResponse.of(responsePage);
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
