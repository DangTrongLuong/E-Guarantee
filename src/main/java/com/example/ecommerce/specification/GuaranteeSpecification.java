package com.example.ecommerce.specification;

import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.GuaranteeRequest;
import com.example.ecommerce.enums.GuaranteeStatus;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.exception.BadRequestException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuaranteeSpecification {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public static Specification<GuaranteeRequest> filterByParams(Map<String, Object> params) {
        return (root, query, criteriaBuilder) -> {
            if (params == null || params.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // Join Customer table cho tên khách hàng và CIF
            Join<GuaranteeRequest, Customer> customerJoin = null;

            // Keyword search: Tìm kiếm chung trong requestCode, customerName, customerCif
            String keyword = extractStringParam(params, "keyword", "search");
            if (keyword != null && !keyword.isBlank()) {
                String keywordLower = keyword.trim().toLowerCase();
                List<Predicate> keywordPredicates = new ArrayList<>();
                
                // Tìm trong mã yêu cầu
                keywordPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("id")),
                        "%" + keywordLower + "%"
                ));
                
                // Tìm trong tên khách hàng
                if (customerJoin == null) {
                    customerJoin = root.join("customer", JoinType.LEFT);
                }
                keywordPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(customerJoin.get("customerName")),
                        "%" + keywordLower + "%"
                ));
                
                // Tìm trong CIF
                keywordPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(customerJoin.get("cif")),
                        "%" + keywordLower + "%"
                ));
                
                // Kết hợp với OR: keyword phải match 1 trong 3 trường
                predicates.add(criteriaBuilder.or(keywordPredicates.toArray(new Predicate[0])));
            }

            // 1. Mã yêu cầu - Tìm kiếm cụ thể (id / requestCode / code / maYeuCau)
            String requestCode = extractStringParam(params, "requestCode", "id", "code", "maYeuCau");
            if (requestCode != null && !requestCode.isBlank() && keyword == null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("id")),
                        "%" + requestCode.trim().toLowerCase() + "%"
                ));
            }

            // 2. Tên khách hàng - Tìm kiếm cụ thể (customerName / tenKhachHang)
            String customerName = extractStringParam(params, "customerName", "tenKhachHang", "customer_name");
            if (customerName != null && !customerName.isBlank() && keyword == null) {
                if (customerJoin == null) {
                    customerJoin = root.join("customer", JoinType.LEFT);
                }
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(customerJoin.get("customerName")),
                        "%" + customerName.trim().toLowerCase() + "%"
                ));
            }

            // 3. Mã CIF - Tìm kiếm cụ thể (cif / customerCif)
            String cif = extractStringParam(params, "cif", "customerCif", "customer_cif");
            if (cif != null && !cif.isBlank() && keyword == null) {
                if (customerJoin == null) {
                    customerJoin = root.join("customer", JoinType.LEFT);
                }
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(customerJoin.get("cif")),
                        "%" + cif.trim().toLowerCase() + "%"
                ));
            }

            // 4. Mã số thuế (taxCode / customerTaxCode / maSoThue)
            String taxCode = extractStringParam(params, "taxCode", "customerTaxCode", "maSoThue", "tax_code");
            if (taxCode != null && !taxCode.isBlank()) {
                if (customerJoin == null) {
                    customerJoin = root.join("customer", JoinType.LEFT);
                }
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(customerJoin.get("taxCode")),
                        "%" + taxCode.trim().toLowerCase() + "%"
                ));
            }

            // 5. Trạng thái (status / trangThai)
            Object statusObj = extractParam(params, "status", "trangThai");
            GuaranteeStatus status = parseEnum(statusObj, GuaranteeStatus.class, "status");
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // 6. Loại bảo lãnh (guaranteeType / loaiBaoLanh / type)
            Object typeObj = extractParam(params, "guaranteeType", "loaiBaoLanh", "type");
            GuaranteeType guaranteeType = parseEnum(typeObj, GuaranteeType.class, "guaranteeType");
            if (guaranteeType != null) {
                predicates.add(criteriaBuilder.equal(root.get("guaranteeType"), guaranteeType));
            }

            // 7. Ngày tạo - Từ ngày (fromDate / fromCreatedDate / tuNgay / startDate / createdFrom)
            Object fromDateObj = extractParam(params, "fromDate", "fromCreatedDate", "tuNgay", "startDate", "createdFrom");
            LocalDateTime fromDateTime = parseFromDate(fromDateObj, "fromDate");
            if (fromDateTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), fromDateTime));
            }

            // 8. Ngày tạo - Đến ngày (toDate / toCreatedDate / denNgay / endDate / createdTo)
            Object toDateObj = extractParam(params, "toDate", "toCreatedDate", "denNgay", "endDate", "createdTo");
            LocalDateTime toDateTime = parseToDate(toDateObj, "toDate");
            if (toDateTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), toDateTime));
            }

            if (fromDateTime != null && toDateTime != null && fromDateTime.isAfter(toDateTime)) {
                throw new BadRequestException("Giá trị fromDate phải nhỏ hơn hoặc bằng toDate");
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Object extractParam(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            if (params.containsKey(key) && params.get(key) != null) {
                return params.get(key);
            }
        }
        return null;
    }

    private static String extractStringParam(Map<String, Object> params, String... keys) {
        Object val = extractParam(params, keys);
        if (val == null) {
            return null;
        }
        String str = val.toString().trim();
        return str.isEmpty() ? null : str;
    }

    private static <E extends Enum<E>> E parseEnum(Object obj, Class<E> enumClass, String fieldName) {
        if (obj == null) {
            return null;
        }
        if (enumClass.isInstance(obj)) {
            return enumClass.cast(obj);
        }
        String str = obj.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(str)) {
                return constant;
            }
        }
        String acceptedValues = String.join(", ", java.util.Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .toList());
        throw new BadRequestException(
                String.format("Giá trị '%s' không hợp lệ cho %s. Giá trị cho phép: [%s]", str, fieldName, acceptedValues)
        );
    }

    private static LocalDateTime parseFromDate(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (obj instanceof LocalDate ld) {
            return ld.atStartOfDay();
        }
        String dateStr = obj.toString().trim();
        if (dateStr.isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return java.time.OffsetDateTime.parse(dateStr, formatter).toLocalDateTime();
                }
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    return date.atStartOfDay();
                } catch (DateTimeParseException ignoredAgain) {
                }
            }
        }
        throw new BadRequestException(
                String.format("Định dạng %s không hợp lệ: '%s'. Ví dụ hợp lệ: 2026-09-17, 2026-09-17T10:30:00, 17/09/2026", fieldName, dateStr)
        );
    }

    private static LocalDateTime parseToDate(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (obj instanceof LocalDate ld) {
            return ld.atTime(LocalTime.MAX);
        }
        String dateStr = obj.toString().trim();
        if (dateStr.isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return java.time.OffsetDateTime.parse(dateStr, formatter).toLocalDateTime();
                }
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    return date.atTime(LocalTime.MAX);
                } catch (DateTimeParseException ignoredAgain) {
                }
            }
        }
        throw new BadRequestException(
                String.format("Định dạng %s không hợp lệ: '%s'. Ví dụ hợp lệ: 2026-09-17, 2026-09-17T23:59:59, 17/09/2026", fieldName, dateStr)
        );
    }
}
