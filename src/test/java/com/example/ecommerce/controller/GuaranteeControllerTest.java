package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.GuaranteeRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class GuaranteeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuaranteeRequestRepository guaranteeRequestRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        guaranteeRequestRepository.deleteAll();

        sampleCustomer = customerRepository.findById("0012345678")
                .map(c -> {
                    c.setCustomerName("Công ty ABC");
                    c.setTaxCode("0101234567");
                    return customerRepository.save(c);
                })
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .cif("0012345678")
                        .customerName("Công ty ABC")
                        .taxCode("0101234567")
                        .build()));
    }

    @Test
    @DisplayName("Tạo mới bảo lãnh trạng thái DRAFT - Thành công")
    void createGuarantee_Success() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.BID_BOND)
                .guaranteeAmount(new BigDecimal("5000000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(90))
                .tenderNumber("TB-2026-001")
                .purpose("Bảo lãnh dự thầu dự án hạ tầng")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .phoneNumber("0912345678")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.customerCif", is("0012345678")))
                .andExpect(jsonPath("$.data.id", startsWith("GR-2026-")))
                .andExpect(jsonPath("$.data.guaranteeDays", is(90)));
    }

    @Test
    @DisplayName("Tạo mới thất bại khi Ngày hết hạn <= Ngày hiệu lực")
    void createGuarantee_InvalidDates() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().minusDays(1))
                .purpose("Bảo lãnh thực hiện hợp đồng")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Ngày hết hạn phải sau ngày hiệu lực")));
    }

    @Test
    @DisplayName("Tạo mới thất bại khi Ngày hiệu lực trong quá khứ")
    void createGuarantee_PastEffectiveDate() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now().minusDays(2))
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh thực hiện hợp đồng")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("Tạo mới thất bại khi Mã số thuế không khớp với CIF của khách hàng")
    void createGuarantee_TaxCodeMismatch() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("9999999999")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh thực hiện hợp đồng")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Mã số thuế không khớp")));
    }

    @Test
    @DisplayName("Tạo mới thất bại khi Tên khách hàng không khớp với CIF")
    void createGuarantee_CustomerNameMismatch() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Tên Công ty Sai")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh thực hiện hợp đồng")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Tên khách hàng không khớp")));
    }

    @Test
    @DisplayName("Tạo mới thất bại khi BID_BOND thiếu Tender Number")
    void createGuarantee_MissingTenderNumber() throws Exception {
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.BID_BOND)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .tenderNumber("")
                .purpose("Bảo lãnh dự thầu")
                .beneficiaryName("Ban quản lý")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Số hiệu gói thầu là bắt buộc")));
    }

    @Test
    @DisplayName("Cập nhật bảo lãnh DRAFT - Thành công")
    void updateGuarantee_Success() throws Exception {
        GuaranteeRequest existing = guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000001")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("2000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Cũ")
                .beneficiaryName("Bên thụ hưởng cũ")
                .contactEmail("old@abc.com")
                .status(GuaranteeStatus.DRAFT)
                .createdDate(LocalDateTime.now())
                .build());

        GuaranteeCreationRequest updateRequest = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("10000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(60))
                .purpose("Mục đích mới đã cập nhật")
                .beneficiaryName("Bên thụ hưởng mới")
                .contactEmail("new@abc.com")
                .build();

        mockMvc.perform(put("/api/v1/guarantees/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.beneficiaryName", is("Bên thụ hưởng mới")))
                .andExpect(jsonPath("$.data.status", is("DRAFT")));
    }

    @Test
    @DisplayName("Cập nhật hồ sơ REJECTED sẽ tự động chuyển về DRAFT")
    void updateGuarantee_RejectedToDraft() throws Exception {
        GuaranteeRequest existing = guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000002")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("2000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Hồ sơ bị từ chối")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .status(GuaranteeStatus.REJECTED)
                .createdDate(LocalDateTime.now())
                .build());

        GuaranteeCreationRequest updateRequest = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("2000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Hồ sơ đã sửa đổi sau khi bị từ chối")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(put("/api/v1/guarantees/" + existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")));
    }
}
