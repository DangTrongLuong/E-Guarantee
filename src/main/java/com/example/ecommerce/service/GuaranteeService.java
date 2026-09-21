package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.request.GuaranteeUpdateRequest;
import com.example.ecommerce.dto.request.RejectGuaranteeRequest;
import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.dto.response.GuaranteeSummaryResponse;
import com.example.ecommerce.dto.response.ProcessingHistoryResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.entity.ProcessingHistory;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.Action;
import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.GuaranteeMapper;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.GuaranteeRequestRepository;
import com.example.ecommerce.repository.ProcessingHistoryRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.validation.CustomerInfoAware;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.specification.GuaranteeSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GuaranteeService {

    GuaranteeRequestRepository guaranteeRequestRepository;
    CustomerRepository customerRepository;
    GuaranteeMapper guaranteeMapper;
    UserRepository userRepository;
    ProcessingHistoryRepository processingHistoryRepository;

    @Transactional
    public GuaranteeResponse createGuarantee(GuaranteeCreationRequest request) {
        User currentUser = getCurrentUser();

        Customer customer = getAndValidateCustomer(request);

        GuaranteeRequest guaranteeRequest = guaranteeMapper.toEntity(request);
        guaranteeRequest.setId(generateRequestId());
        guaranteeRequest.setCustomer(customer);
        guaranteeRequest.setStatus(GuaranteeStatus.DRAFT);
        guaranteeRequest.setCreatedBy(currentUser);
        guaranteeRequest.setCreatedDate(LocalDateTime.now());

        if (guaranteeRequest.getCurrency() == null) {
            guaranteeRequest.setCurrency(Currency.VND);
        }

        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);

        createHistory(saved, Action.CREATE, currentUser, null);

        log.info("Tạo thành công yêu cầu bảo lãnh ở trạng thái DRAFT: {} bởi user: {}", saved.getId(), currentUser.getUsername());

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional
    public GuaranteeResponse updateGuarantee(String id, GuaranteeUpdateRequest request) {
        User currentUser = getCurrentUser();

        GuaranteeRequest existing = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (existing.getStatus() != GuaranteeStatus.DRAFT
                && existing.getStatus() != GuaranteeStatus.REJECTED) {
            throw new BadRequestException("Chỉ được chỉnh sửa hồ sơ ở trạng thái Bản nháp (DRAFT) hoặc Bị từ chối (REJECTED)");
        }

        Customer customer = getAndValidateCustomer(request);

        guaranteeMapper.updateEntityFromRequest(request, existing);
        existing.setCustomer(customer);

        if (existing.getStatus() == GuaranteeStatus.REJECTED) {
            existing.setStatus(GuaranteeStatus.DRAFT);
        }

        existing.setUpdatedDate(LocalDateTime.now());
        existing.setUpdatedBy(currentUser);

        GuaranteeRequest saved = guaranteeRequestRepository.save(existing);

        createHistory(saved, Action.UPDATE, currentUser, null);

        log.info("Cập nhật thành công yêu cầu bảo lãnh {}: trạng thái {} bởi user: {}", saved.getId(), saved.getStatus(), currentUser.getUsername());

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public GuaranteeResponse getDetail(String id) {
        GuaranteeRequest guaranteeRequest = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        return guaranteeMapper.toResponse(guaranteeRequest);
    }

    @Transactional(readOnly = true)
    public List<ProcessingHistoryResponse> getHistories(String id) {
        if (!guaranteeRequestRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id);
        }

        return processingHistoryRepository
                .findByGuaranteeRequest_IdOrderByTimestampAsc(id)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public GuaranteeResponse submit(String id) {
        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.MARKER) {
            throw new BadRequestException("Chỉ MARKER mới được gửi hồ sơ phê duyệt");
        }

        GuaranteeRequest guaranteeRequest = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (guaranteeRequest.getStatus() != GuaranteeStatus.DRAFT) {
            throw new BadRequestException("Chỉ hồ sơ ở trạng thái DRAFT mới được gửi phê duyệt");
        }

        validateBeforeSubmit(guaranteeRequest);

        guaranteeRequest.setStatus(GuaranteeStatus.PENDING_APPROVAL);
        guaranteeRequest.setUpdatedBy(currentUser);
        guaranteeRequest.setUpdatedDate(LocalDateTime.now());

        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);

        createHistory(saved, Action.SUBMIT, currentUser, null);

        log.info("Gửi phê duyệt thành công yêu cầu bảo lãnh {} bởi {}", saved.getId(), currentUser.getUsername());

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional
    public GuaranteeResponse approve(String id) {
        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHECKER) {
            throw new BadRequestException("Chỉ CHECKER mới được phê duyệt hồ sơ");
        }

        GuaranteeRequest guaranteeRequest = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (guaranteeRequest.getStatus() != GuaranteeStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ hồ sơ ở trạng thái PENDING_APPROVAL mới được phê duyệt");
        }

        guaranteeRequest.setStatus(GuaranteeStatus.APPROVED);
        guaranteeRequest.setUpdatedBy(currentUser);
        guaranteeRequest.setUpdatedDate(LocalDateTime.now());

        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);

        createHistory(saved, Action.APPROVE, currentUser, null);

        log.info("Phê duyệt thành công yêu cầu bảo lãnh {} bởi {}", saved.getId(), currentUser.getUsername());

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional
    public GuaranteeResponse reject(String id, RejectGuaranteeRequest request) {
        User currentUser = getCurrentUser();

        if (currentUser.getRole() != Role.CHECKER) {
            throw new BadRequestException("Chỉ CHECKER mới được từ chối hồ sơ");
        }

        GuaranteeRequest guaranteeRequest = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (guaranteeRequest.getStatus() != GuaranteeStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ hồ sơ ở trạng thái PENDING_APPROVAL mới được từ chối");
        }

        guaranteeRequest.setStatus(GuaranteeStatus.REJECTED);
        guaranteeRequest.setUpdatedBy(currentUser);
        guaranteeRequest.setUpdatedDate(LocalDateTime.now());

        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);

        createHistory(saved, Action.REJECT, currentUser, request.getReason().trim());

        log.info("Từ chối yêu cầu bảo lãnh {} bởi {}", saved.getId(), currentUser.getUsername());

        return guaranteeMapper.toResponse(saved);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("Không xác định được người dùng hiện tại");
        }

        String username;
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            username = jwt.getSubject();
        } else {
            username = authentication.getName();
        }

        if (username == null || username.isBlank()) {
            throw new BadRequestException("Không xác định được người dùng hiện tại");
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private void createHistory(
            GuaranteeRequest guaranteeRequest,
            Action action,
            User user,
            String comment
    ) {
        ProcessingHistory processingHistory = ProcessingHistory.builder()
                .guaranteeRequest(guaranteeRequest)
                .action(action)
                .performedBy(user)
                .role(user.getRole())
                .timestamp(LocalDateTime.now())
                .comment(comment)
                .build();

        processingHistoryRepository.save(processingHistory);

        log.info("Lưu lịch sử: action={}, user={}, guaranteeId={}", action, user.getUsername(), guaranteeRequest.getId());
    }

    private ProcessingHistoryResponse toHistoryResponse(ProcessingHistory history) {
        ProcessingHistoryResponse response = guaranteeMapper.toHistoryResponse(history);

        if (history.getGuaranteeRequest() != null) {
            response.setGuaranteeId(history.getGuaranteeRequest().getId());
        }

        if (history.getPerformedBy() != null) {
            response.setPerformedBy(history.getPerformedBy().getUsername());
            response.setPerformedByFullName(history.getPerformedBy().getFullName());
        }

        return response;
    }

    private Customer getAndValidateCustomer(CustomerInfoAware request) {
        Customer customer = customerRepository.findById(request.getCustomerCif())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khách hàng với mã CIF " + request.getCustomerCif()
                                + " không tồn tại trong hệ thống. Vui lòng kiểm tra lại!"
                ));

        if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
            if (customer.getCustomerName() != null && !customer.getCustomerName().isBlank()) {
                if (!customer.getCustomerName().trim().equalsIgnoreCase(request.getCustomerName().trim())) {
                    throw new BadRequestException("Tên khách hàng không khớp với thông tin khách hàng có mã CIF " + request.getCustomerCif());
                }
            }
        }

        if (request.getTaxCode() != null && !request.getTaxCode().isBlank()) {
            if (customer.getTaxCode() != null && !customer.getTaxCode().isBlank()) {
                if (!customer.getTaxCode().trim().equalsIgnoreCase(request.getTaxCode().trim())) {
                    throw new BadRequestException("Mã số thuế không khớp với thông tin khách hàng có mã CIF " + request.getCustomerCif());
                }
            }
        }

        return customer;
    }

    private synchronized String generateRequestId() {
        long count = guaranteeRequestRepository.count() + 1;
        String id;

        do {
            id = String.format("GR-2026-%06d", count++);
        } while (guaranteeRequestRepository.existsById(id));

        return id;
    }

    private void validateBeforeSubmit(GuaranteeRequest guaranteeRequest) {
        if (guaranteeRequest.getCustomer() == null) {
            throw new BadRequestException("Thông tin khách hàng không được để trống");
        }

        if (guaranteeRequest.getCustomer().getCif() == null
                || !guaranteeRequest.getCustomer().getCif().matches("^[0-9]{6,12}$")) {
            throw new BadRequestException("Mã CIF phải từ 6 đến 12 chữ số");
        }
        if (guaranteeRequest.getGuaranteeType() == null) {
            throw new BadRequestException("Loại bảo lãnh không được để trống");
        }

        if (guaranteeRequest.getGuaranteeAmount() == null
                || guaranteeRequest.getGuaranteeAmount().signum() <= 0) {
            throw new BadRequestException("Số tiền bảo lãnh phải lớn hơn 0");
        }

        if (guaranteeRequest.getEffectiveDate() == null) {
            throw new BadRequestException("Ngày hiệu lực không được để trống");
        }

        if (guaranteeRequest.getExpiryDate() == null
                || !guaranteeRequest.getExpiryDate().isAfter(guaranteeRequest.getEffectiveDate())) {
            throw new BadRequestException("Ngày hết hạn phải sau ngày hiệu lực");
        }

        if (guaranteeRequest.getGuaranteeType() == GuaranteeType.BID_BOND
                && (guaranteeRequest.getTenderNumber() == null
                || guaranteeRequest.getTenderNumber().isBlank())) {
            throw new BadRequestException("Số hiệu gói thầu là bắt buộc khi loại bảo lãnh là BID_BOND");
        }

        if (guaranteeRequest.getPurpose() == null
                || guaranteeRequest.getPurpose().isBlank()) {
            throw new BadRequestException("Mục đích bảo lãnh không được để trống");
        }

        if (guaranteeRequest.getBeneficiaryName() == null
                || guaranteeRequest.getBeneficiaryName().isBlank()) {
            throw new BadRequestException("Tên bên thụ hưởng không được để trống");
        }

        if (guaranteeRequest.getContactEmail() == null
                || guaranteeRequest.getContactEmail().isBlank()) {
            throw new BadRequestException("Email liên hệ không được để trống");
        }

        if (guaranteeRequest.getPhoneNumber() != null
                && !guaranteeRequest.getPhoneNumber().isBlank()
                && !guaranteeRequest.getPhoneNumber().matches("^[0-9]{10}$")) {
            throw new BadRequestException("Số điện thoại phải chứa 10 chữ số");
        }
    }

    public PageResponse<GuaranteeSummaryResponse> getGuarantees(Map<String, Object> params) {
        return getGuarantees(params, null);
    }

    public PageResponse<GuaranteeSummaryResponse> getGuarantees(Map<String, Object> params, List<String> sortByParams) {
        log.info("Tìm kiếm và lọc danh sách yêu cầu bảo lãnh với params: {}", params);
        Specification<GuaranteeRequest> spec = GuaranteeSpecification.filterByParams(params);
        Pageable pageable = createPageable(params, sortByParams);

        Page<GuaranteeRequest> guaranteePage = guaranteeRequestRepository.findAll(spec, pageable);
        Page<GuaranteeSummaryResponse> summaryPage = guaranteePage.map(guaranteeMapper::toSummaryResponse);

        return PageResponse.of(summaryPage);
    }

    public List<GuaranteeSummaryResponse> getGuarantees() {
        List<GuaranteeRequest> guarantees = guaranteeRequestRepository.findAll();
        return guaranteeMapper.toResponseList(guarantees);
    }

    private Pageable createPageable(Map<String, Object> params, List<String> sortByParams) {
        int page = 0;
        int size = 5; // Mặc định phân trang 5 bản ghi theo yêu cầu

        if (params != null) {
            if (params.containsKey("page") && params.get("page") != null) {
                try {
                    int p = Integer.parseInt(params.get("page").toString().trim());
                    if (p < 0) {
                        throw new BadRequestException("Tham số page phải lớn hơn hoặc bằng 0");
                    }
                    page = p;
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("Tham số page phải là số nguyên hợp lệ");
                }
            }

            if (params.containsKey("size") && params.get("size") != null) {
                try {
                    int s = Integer.parseInt(params.get("size").toString().trim());
                    if (s <= 0) {
                        throw new BadRequestException("Tham số size phải lớn hơn 0");
                    }
                    size = s;
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("Tham số size phải là số nguyên hợp lệ");
                }
            }
        }

        Sort.Direction defaultDirection = resolveSortDirection(params);
        List<Sort.Order> orders = buildSortOrders(params, sortByParams, defaultDirection);

        if (orders.isEmpty()) {
            orders.add(new Sort.Order(defaultDirection, "id"));
        }

        return PageRequest.of(page, size, Sort.by(orders));
    }

    private Sort.Direction resolveSortDirection(Map<String, Object> params) {
        Sort.Direction direction = Sort.Direction.DESC; // Mặc định sort DESC theo mã id (mã yêu cầu)

        if (params != null) {
            String dirStr = extractSortDirectionParam(params);
            if (dirStr != null && !dirStr.isBlank()) {
                direction = parseDirection(dirStr.trim(), "sortDirection");
            }
        }

        return direction;
    }

    private String extractSortDirectionParam(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        if (params.containsKey("sortDirection") && params.get("sortDirection") != null) {
            return params.get("sortDirection").toString();
        }
        if (params.containsKey("direction") && params.get("direction") != null) {
            return params.get("direction").toString();
        }
        if (params.containsKey("sortOrder") && params.get("sortOrder") != null) {
            return params.get("sortOrder").toString();
        }
        if (params.containsKey("sort") && params.get("sort") != null) {
            return params.get("sort").toString();
        }
        return null;
    }

    private List<Sort.Order> buildSortOrders(Map<String, Object> params, List<String> sortByParams, Sort.Direction defaultDirection) {
        List<Sort.Order> orders = new ArrayList<>();
        List<String> requestedSorts = new ArrayList<>();

        if (sortByParams != null) {
            for (String value : sortByParams) {
                if (value != null && !value.isBlank()) {
                    requestedSorts.add(value.trim());
                }
            }
        }

        if (requestedSorts.isEmpty() && params != null) {
            Object sortField = params.get("sortBy");
            if (sortField == null) {
                sortField = params.get("sortField");
            }
            if (sortField == null) {
                sortField = params.get("orderBy");
            }
            if (sortField != null && !sortField.toString().isBlank()) {
                requestedSorts.add(sortField.toString().trim());
            }
        }

        for (String sortExpression : requestedSorts) {
            String[] parts = sortExpression.split(",", 2);
            String rawField = parts[0].trim();
            if (rawField.isBlank()) {
                continue;
            }

            String mappedField = resolveSortField(rawField);
            Sort.Direction direction = defaultDirection;
            if (parts.length == 2 && !parts[1].isBlank()) {
                direction = parseDirection(parts[1].trim(), "sortBy");
            }

            orders.add(new Sort.Order(direction, mappedField));
        }

        return orders;
    }

    // Hàm sử lý chỉ lấy những field nằm trong whitelist.
    private String resolveSortField(String field) {
        return switch (field) {
            case "id", "createdDate", "guaranteeAmount", "status", "guaranteeType" -> field;
            case "customerName", "customer_name", "tenKhachHang" -> "customer.customerName";
            default -> throw new BadRequestException(
                    "sortBy không hợp lệ. Trường hợp lệ: id, createdDate, guaranteeAmount, status, guaranteeType, customerName"
            );
        };
    }

    private Sort.Direction parseDirection(String direction, String fieldName) {
        if ("asc".equalsIgnoreCase(direction) || "ascending".equalsIgnoreCase(direction)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equalsIgnoreCase(direction) || "descending".equalsIgnoreCase(direction)) {
            return Sort.Direction.DESC;
        }
        if ("sortBy".equals(fieldName)) {
            throw new BadRequestException("Hướng sắp xếp trong sortBy không hợp lệ. Chỉ chấp nhận asc hoặc desc");
        }
        throw new BadRequestException("sortDirection không hợp lệ. Chỉ chấp nhận asc hoặc desc");
    }
}
