package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.GuaranteeCreationRequest;
import com.example.ecommerce.dto.response.GuaranteeResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.entity.ProcessingHistory;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.Action;
import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeStatus;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        log.info("Tạo thành công yêu cầu bảo lãnh ở trạng thái DRAFT: {} bởi user: {}", saved.getId(), currentUser.getUsername());

        saveHistory(saved, currentUser, Action.CREATE, null);

        return guaranteeMapper.toResponse(saved);
    }

    @Transactional
    public GuaranteeResponse updateGuarantee(String id, GuaranteeCreationRequest request) {
        User currentUser = getCurrentUser();

        GuaranteeRequest existing = guaranteeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu bảo lãnh với mã: " + id));

        if (existing.getStatus() != GuaranteeStatus.DRAFT && existing.getStatus() != GuaranteeStatus.REJECTED) {
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
        log.info("Cập nhật thành công yêu cầu bảo lãnh {}: trạng thái {} bởi user: {}", saved.getId(), saved.getStatus(), currentUser.getUsername());

        saveHistory(saved, currentUser, Action.UPDATE, null);

        return guaranteeMapper.toResponse(saved);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("Bạn chưa đăng nhập. Vui lòng đăng nhập để thực hiện thao tác này.");
        }

        String username;
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            username = jwt.getSubject();
        } else {
            username = authentication.getName();
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));
    }

    private void saveHistory(GuaranteeRequest guaranteeRequest, User performedBy, Action action, String comment) {
        ProcessingHistory history = ProcessingHistory.builder()
                .guaranteeRequest(guaranteeRequest)
                .performedBy(performedBy)
                .role(performedBy.getRole())
                .action(action)
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();
        processingHistoryRepository.save(history);
        log.info("Lưu lịch sử: action={}, user={}, guaranteeId={}", action, performedBy.getUsername(), guaranteeRequest.getId());
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
}

