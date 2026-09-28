package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.request.GuaranteeUpdateRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.Status;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.GuaranteeRequestRepository;
import com.example.ecommerce.repository.ProcessingHistoryRepository;
import com.example.ecommerce.repository.UserRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private ProcessingHistoryRepository processingHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.ecommerce.repository.GuaranteeFileRepository guaranteeFileRepository;

    private Customer sampleCustomer;
    private static final String TEST_USERNAME = "maker_test";

    @BeforeEach
    void setUp() {
        guaranteeFileRepository.deleteAll();
        processingHistoryRepository.deleteAll();
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

        userRepository.findByUsername(TEST_USERNAME).orElseGet(() -> {
            User u = User.builder()
                    .username(TEST_USERNAME)
                    .password("test")
                    .fullName("Test Marker")
                    .role(Role.MARKER)
                    .status(Status.ACTIVE)
                    .build();
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
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
                        .with(user(TEST_USERNAME).roles("MARKER"))
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
                .phoneNumber("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.data[0]", containsString("Ngày hết hạn phải bằng hoặc sau")));
    }

    @Test
    @DisplayName("Tạo mới thành công khi Ngày hiệu lực và Ngày hết hạn trùng nhau (cùng 1 ngày)")
    void createGuarantee_SameDayEffectiveAndExpiry_Success() throws Exception {
        LocalDate today = LocalDate.now();
        GuaranteeCreationRequest request = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(today)
                .expiryDate(today)
                .purpose("Bảo lãnh trong ngày")
                .beneficiaryName("Ban quản lý dự án XYZ")
                .contactEmail("contact@abc.com")
                .phoneNumber("0912345678")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.guaranteeDays", is(0)));
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
                .phoneNumber("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
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
                .phoneNumber("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
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
                .phoneNumber("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
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
                .phoneNumber("0987654321")
                .build();

        mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.data[0]", containsString("Số hiệu gói thầu là bắt buộc")));
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

        GuaranteeUpdateRequest updateRequest = GuaranteeUpdateRequest.builder()
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
                        .with(user(TEST_USERNAME).roles("MARKER"))
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

        GuaranteeUpdateRequest updateRequest = GuaranteeUpdateRequest.builder()
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
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("DRAFT")));
    }

    @Test
    @DisplayName("Cập nhật hồ sơ với Ngày hiệu lực trong quá khứ - Thành công")
    void updateGuarantee_PastEffectiveDate_Success() throws Exception {
        LocalDate pastEffectiveDate = LocalDate.now().minusDays(5);
        LocalDate futureExpiryDate = LocalDate.now().plusDays(30);

        GuaranteeRequest existing = guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000005")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("5000000"))
                .currency(Currency.VND)
                .effectiveDate(pastEffectiveDate)
                .expiryDate(futureExpiryDate)
                .purpose("Hồ sơ tạo trong quá khứ")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .status(GuaranteeStatus.DRAFT)
                .createdDate(LocalDateTime.now().minusDays(5))
                .build());

        GuaranteeUpdateRequest updateRequest = GuaranteeUpdateRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("6000000"))
                .currency(Currency.VND)
                .effectiveDate(pastEffectiveDate)
                .expiryDate(futureExpiryDate)
                .purpose("Hồ sơ đã cập nhật thông tin mới giữ nguyên ngày hiệu lực quá khứ")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .build();

        mockMvc.perform(put("/api/v1/guarantees/" + existing.getId())
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.guaranteeAmount", is(6000000)));
    }

    @Test
    @DisplayName("Xóa thành công yêu cầu bảo lãnh ở trạng thái DRAFT")
    void deleteGuarantee_Draft_Success() throws Exception {
        GuaranteeRequest existing = guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000003")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.BID_BOND)
                .tenderNumber("TB-2026-003")
                .guaranteeAmount(new BigDecimal("3000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Xóa bản nháp")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .status(GuaranteeStatus.DRAFT)
                .createdDate(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/v1/guarantees/" + existing.getId())
                        .with(user(TEST_USERNAME).roles("MARKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Xóa yêu cầu bảo lãnh thành công")));
    }

    @Test
    @DisplayName("Xóa thất bại khi yêu cầu bảo lãnh không ở trạng thái DRAFT")
    void deleteGuarantee_NonDraft_BadRequest() throws Exception {
        GuaranteeRequest existing = guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000004")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.BID_BOND)
                .tenderNumber("TB-2026-004")
                .guaranteeAmount(new BigDecimal("3000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Không thể xóa")
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .status(GuaranteeStatus.PENDING_APPROVAL)
                .createdDate(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/v1/guarantees/" + existing.getId())
                        .with(user(TEST_USERNAME).roles("MARKER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Chỉ được xóa hồ sơ ở trạng thái Bản nháp")));
    }

    @Test
    @DisplayName("Lấy số lượng yêu cầu bảo lãnh theo trạng thái - Thành công")
    void getGuaranteeStatusCounts_Success() throws Exception {
        // Tạo các bản ghi với từng trạng thái
        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000001")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.BID_BOND)
                .tenderNumber("TB-2026-001")
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng 1")
                .contactEmail("contact1@abc.com")
                .status(GuaranteeStatus.DRAFT)
                .createdDate(LocalDateTime.now())
                .build());

        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000002")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.PERFORMANCE)
                .guaranteeAmount(new BigDecimal("2000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng 2")
                .contactEmail("contact2@abc.com")
                .status(GuaranteeStatus.PENDING_APPROVAL)
                .createdDate(LocalDateTime.now())
                .build());

        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000003")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.ADVANCE_PAYMENT)
                .guaranteeAmount(new BigDecimal("3000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng 3")
                .contactEmail("contact3@abc.com")
                .status(GuaranteeStatus.APPROVED)
                .createdDate(LocalDateTime.now())
                .build());

        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000004")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("4000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng 4")
                .contactEmail("contact4@abc.com")
                .status(GuaranteeStatus.REJECTED)
                .createdDate(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/guarantees/status-counts")
                        .with(user(TEST_USERNAME).roles("MARKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ALL", is(4)))
                .andExpect(jsonPath("$.data.DRAFT", is(1)))
                .andExpect(jsonPath("$.data.PENDING_APPROVAL", is(1)))
                .andExpect(jsonPath("$.data.APPROVED", is(1)))
                .andExpect(jsonPath("$.data.REJECTED", is(1)));
    }

    @Test
    @DisplayName("Lấy số lượng yêu cầu bảo lãnh theo trạng thái có lọc điều kiện - Thành công")
    void getGuaranteeStatusCounts_WithFilter() throws Exception {
        Customer otherCustomer = customerRepository.save(Customer.builder()
                .cif("0099999999")
                .customerName("Tập đoàn XYZ")
                .taxCode("0109999999")
                .build());

        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000010")
                .customer(sampleCustomer)
                .guaranteeType(GuaranteeType.BID_BOND)
                .tenderNumber("TB-2026-010")
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng")
                .contactEmail("contact@abc.com")
                .status(GuaranteeStatus.DRAFT)
                .createdDate(LocalDateTime.now())
                .build());

        guaranteeRequestRepository.save(GuaranteeRequest.builder()
                .id("GR-2026-000011")
                .customer(otherCustomer)
                .guaranteeType(GuaranteeType.BID_BOND)
                .tenderNumber("TB-2026-011")
                .guaranteeAmount(new BigDecimal("5000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .beneficiaryName("Bên thụ hưởng khác")
                .contactEmail("other@xyz.com")
                .status(GuaranteeStatus.APPROVED)
                .createdDate(LocalDateTime.now())
                .build());

        // Lọc theo CIF của sampleCustomer
        mockMvc.perform(get("/api/v1/guarantees/status-counts")
                        .param("cif", "0012345678")
                        .with(user(TEST_USERNAME).roles("CHECKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ALL", is(1)))
                .andExpect(jsonPath("$.data.DRAFT", is(1)))
                .andExpect(jsonPath("$.data.PENDING_APPROVAL", is(0)))
                .andExpect(jsonPath("$.data.APPROVED", is(0)))
                .andExpect(jsonPath("$.data.REJECTED", is(0)));
    }

    @Test
    @DisplayName("Cập nhật bảo lãnh - Đồng bộ danh sách file khi thêm/bớt hoặc xóa hết publicIds")
    void updateGuarantee_SyncFiles_Success() throws Exception {
        // Prepare 2 files in database
        com.example.ecommerce.entity.GuaranteeFile file1 = guaranteeFileRepository.save(com.example.ecommerce.entity.GuaranteeFile.builder()
                .fileName("file1.pdf")
                .fileUrl("http://cloudinary.com/file1.pdf")
                .publicId("pub_001")
                .build());

        com.example.ecommerce.entity.GuaranteeFile file2 = guaranteeFileRepository.save(com.example.ecommerce.entity.GuaranteeFile.builder()
                .fileName("file2.pdf")
                .fileUrl("http://cloudinary.com/file2.pdf")
                .publicId("pub_002")
                .build());

        // Create guarantee with 2 files
        GuaranteeCreationRequest createReq = GuaranteeCreationRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh test file")
                .beneficiaryName("Ban quản lý ABC")
                .contactEmail("contact@gmail.com")
                .phoneNumber("0912345678")
                .publicIds(java.util.List.of("pub_001", "pub_002"))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/guarantees")
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.files", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        String guaranteeId = objectMapper.readTree(responseJson).get("data").get("id").asText();

        // Update guarantee by removing pub_002 (keeping only pub_001)
        GuaranteeUpdateRequest updateReq1 = GuaranteeUpdateRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh test file - bớt 1 file")
                .beneficiaryName("Ban quản lý ABC")
                .contactEmail("contact@gmail.com")
                .phoneNumber("0912345678")
                .publicIds(java.util.List.of("pub_001"))
                .build();

        mockMvc.perform(put("/api/v1/guarantees/" + guaranteeId)
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files", hasSize(1)))
                .andExpect(jsonPath("$.data.files[0].publicId", is("pub_001")));

        // Update guarantee by sending empty publicIds list (removing all files)
        GuaranteeUpdateRequest updateReq2 = GuaranteeUpdateRequest.builder()
                .customerCif("0012345678")
                .customerName("Công ty ABC")
                .taxCode("0101234567")
                .guaranteeType(GuaranteeType.OTHER)
                .guaranteeAmount(new BigDecimal("1000000"))
                .currency(Currency.VND)
                .effectiveDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .purpose("Bảo lãnh test file - xóa hết file")
                .beneficiaryName("Ban quản lý ABC")
                .contactEmail("contact@gmail.com")
                .phoneNumber("0912345678")
                .publicIds(java.util.Collections.emptyList())
                .build();

        mockMvc.perform(put("/api/v1/guarantees/" + guaranteeId)
                        .with(user(TEST_USERNAME).roles("MARKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files", hasSize(0)));
    }
}
