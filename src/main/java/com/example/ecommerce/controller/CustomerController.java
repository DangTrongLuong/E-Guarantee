package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.request.CustomerUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer", description = "Customer catalog endpoints")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    CustomerService customerService;

    @PostMapping
    @Operation(
            summary = "Tạo khách hàng",
            description = "Tạo một khách hàng mới"
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerCreationRequest customerCreationRequest
            ){
        CustomerResponse customerResponse = customerService.createCustomer(customerCreationRequest);
        log.info("Tạo khách hàng thành công: {}", customerResponse.getCif());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khách hàng thành công", customerResponse));
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách khách hàng",
            description = "Lấy danh sách tất cả khách hàng"
    )
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomer(){

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy danh sách thành công", customerService.getAllCustomer()));
    }

    @GetMapping("/cif")
    @Operation(
            summary = "Lấy thông tin khách hàng",
            description = "Lấy thông tin chi tiết của một khách hàng theo CIF"
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerByCif(String cif){

        CustomerResponse customerResponse = customerService.getCustomerByCif(cif);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thông tin chi tiết của một khách hàng theo CIF thành công!", customerResponse));
    }

    @PutMapping("/cif")
    @Operation(
            summary = "Cập nhật khách hàng",
            description = "Cập nhật thông tin của một khách hàng theo ID"
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @RequestParam String cif,
            @Valid @RequestBody CustomerUpdateRequest customerUpdateRequest
    ){
        CustomerResponse customerResponse = customerService.updateCustomer(cif, customerUpdateRequest);
        log.info("Cập nhật thành công khách hàng: {}", cif);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật khách hàng thành công", customerResponse));
    }

    @DeleteMapping("/cif")
    @Operation(
            summary = "Xóa khách hàng",
            description = "Xóa một khách hàng theo ID"
    )
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable String cif){
        customerService.deleteCustomer(cif);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa khách hàng thành công !", null));
    }

}
