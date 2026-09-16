package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.request.RejectGuaranteeRequest;
import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.dto.response.ProcessingHistoryResponse;
import com.example.ecommerce.entity.*;
import com.example.ecommerce.enums.*;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.GuaranteeMapper;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.GuaranteeRequestRepository;
import com.example.ecommerce.repository.ProcessingHistoryRepository;
import com.example.ecommerce.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;

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
        validateGuaranteeRequest(request);

        Customer customer = getAndValidateCustomer(request);

        GuaranteeRequest guaranteeRequest = guaranteeMapper.toEntity(request);
        guaranteeRequest.setId(generateRequestId());
        guaranteeRequest.setCustomer(customer);
        guaranteeRequest.setStatus(GuaranteeStatus.DRAFT);
        guaranteeRequest.setCreatedBy(getCurrentUser().getUsername());
        guaranteeRequest.setCreatedDate(LocalDateTime.now());
        if (guaranteeRequest.getCurrency() == null) {
            guaranteeRequest.setCurrency(Currency.VND);
        }

        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);
        log.info("Tạo thành công yêu cầu bảo lãnh ở trạng thái DRAFT: {}", saved.getId());

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional
    public GuaranteeResponse updateGuarantee(String id, GuaranteeCreationRequest request) {
        GuaranteeRequest existing = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (existing.getStatus() != GuaranteeStatus.DRAFT && existing.getStatus() != GuaranteeStatus.REJECTED) {
            throw new BadRequestException("Chỉ được chỉnh sửa hồ sơ ở trạng thái Bản nháp (DRAFT) hoặc Bị từ chối (REJECTED)");
        }

        validateGuaranteeRequest(request);

        Customer customer = getAndValidateCustomer(request);

        guaranteeMapper.updateEntityFromRequest(request, existing);
        existing.setCustomer(customer);

        if (existing.getStatus() == GuaranteeStatus.REJECTED) {
            existing.setStatus(GuaranteeStatus.DRAFT);
        }

        existing.setUpdatedDate(LocalDateTime.now());
        existing.setUpdatedBy(getCurrentUser().getUsername());
        GuaranteeRequest saved = guaranteeRequestRepository.save(existing);
        log.info("Cập nhật thành công yêu cầu bảo lãnh {}: trạng thái hiện tại {}", saved.getId(), saved.getStatus());

        return guaranteeMapper.toResponse(saved);
    }

    private void validateGuaranteeRequest(GuaranteeCreationRequest request) {
        if (request.getEffectiveDate() != null) {
            if (request.getEffectiveDate().isBefore(java.time.LocalDate.now())) {
                throw new BadRequestException("Ngày hiệu lực không được là ngày trong quá khứ");
            }
            if (request.getExpiryDate() != null && !request.getExpiryDate().isAfter(request.getEffectiveDate())) {
                throw new BadRequestException("Ngày hết hạn phải sau ngày hiệu lực");
            }
        }

        if (request.getGuaranteeType() == GuaranteeType.BID_BOND) {
            if (request.getTenderNumber() == null || request.getTenderNumber().isBlank()) {
                throw new BadRequestException("Số hiệu gói thầu là bắt buộc khi loại bảo lãnh là Bảo lãnh dự thầu (BID_BOND)");
            }
        }
    }

    private Customer getAndValidateCustomer(GuaranteeCreationRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerCif())
                .orElseThrow(() -> new ResourceNotFoundException("Khách hàng với mã CIF " + request.getCustomerCif() + " không tồn tại trong hệ thống. Vui lòng kiểm tra lại!"));

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

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BadRequestException("Không xác định được người dùng hiện tại");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
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
                .map(guaranteeMapper::toHistoryResponse)
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
        guaranteeRequest.setUpdatedBy(currentUser.getUsername());
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
        guaranteeRequest.setUpdatedBy(currentUser.getUsername());
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
        guaranteeRequest.setUpdatedBy(currentUser.getUsername());
        guaranteeRequest.setUpdatedDate(LocalDateTime.now());
        GuaranteeRequest saved = guaranteeRequestRepository.save(guaranteeRequest);
        createHistory(saved, Action.REJECT, currentUser, request.getReason().trim());
        log.info("Từ chối yêu cầu bảo lãnh {} bởi {}", saved.getId(), currentUser.getUsername());
        return guaranteeMapper.toResponse(saved);
    }

    private void createHistory(GuaranteeRequest guaranteeRequest, Action action, User user, String comment) {
        ProcessingHistory processingHistory = ProcessingHistory.builder()
                .guaranteeRequest(guaranteeRequest)
                .action(action)
                .performedBy(user.getUsername())
                .role(user.getRole())
                .timestamp(LocalDateTime.now())
                .comment(comment)
                .build();
        processingHistoryRepository.save(processingHistory);
    }

    private void validateBeforeSubmit(GuaranteeRequest guaranteeRequest) {
        if (guaranteeRequest.getCustomer() == null) {
            throw new BadRequestException("Thông tin khách hàng không được để trống");
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
                || !guaranteeRequest.getExpiryDate()
                .isAfter(guaranteeRequest.getEffectiveDate())) {
            throw new BadRequestException("Ngày hết hạn phải sau ngày hiệu lực");
        }

        if (guaranteeRequest.getGuaranteeType() == GuaranteeType.BID_BOND
                && (guaranteeRequest.getTenderNumber() == null || guaranteeRequest.getTenderNumber().isBlank())) {
            throw new BadRequestException("Số hiệu gói thầu là bắt buộc khi loại bảo lãnh là BID_BOND");
        }
    }
}
