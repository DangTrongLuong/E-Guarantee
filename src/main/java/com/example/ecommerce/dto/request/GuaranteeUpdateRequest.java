package com.example.ecommerce.dto.request;

import com.example.ecommerce.enums.Currency;
import com.example.ecommerce.enums.GuaranteeType;
import com.example.ecommerce.validation.CustomerInfoAware;
import com.example.ecommerce.validation.DateRangeAware;
import com.example.ecommerce.validation.TenderNumberAware;
import com.example.ecommerce.validation.ValidExpiryDate;
import com.example.ecommerce.validation.ValidTenderNumber;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidTenderNumber
@ValidExpiryDate
public class GuaranteeUpdateRequest implements DateRangeAware, TenderNumberAware, CustomerInfoAware {

    @NotBlank(message = "Mã CIF không được để trống")
    @Pattern(regexp = "^[0-9]{6,12}$", message = "Mã CIF phải từ 6 đến 12 chữ số")
    private String customerCif;

    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 255, message = "Tên khách hàng không được vượt quá 255 ký tự")
    private String customerName;

    @Size(max = 20, message = "Mã số thuế không được vượt quá 20 ký tự")
    private String taxCode;

    @NotNull(message = "Loại bảo lãnh không được để trống")
    private GuaranteeType guaranteeType;

    @NotNull(message = "Số tiền bảo lãnh không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền bảo lãnh phải lớn hơn 0")
    @DecimalMax(value = "1000000000000.00", message = "Số tiền bảo lãnh không được vượt quá 1.000.000.000.000 VND")
    private BigDecimal guaranteeAmount;

    private Currency currency;

    @NotNull(message = "Ngày hiệu lực không được để trống")
    private LocalDate effectiveDate;

    @NotNull(message = "Ngày hết hạn không được để trống")
    private LocalDate expiryDate;

    @Size(max = 100, message = "Số hiệu gói thầu không được vượt quá 100 ký tự")
    private String tenderNumber;

    @Size(max = 100, message = "Số hợp đồng liên quan không được vượt quá 100 ký tự")
    private String relatedContractNumber;

    @Size(max = 100, message = "Số tham chiếu không được vượt quá 100 ký tự")
    private String referenceNumber;

    @NotBlank(message = "Mục đích bảo lãnh không được để trống")
    @Size(max = 1000, message = "Mục đích bảo lãnh không được vượt quá 1000 ký tự")
    private String purpose;

    @NotBlank(message = "Tên bên thụ hưởng không được để trống")
    @Size(max = 255, message = "Tên bên thụ hưởng không được vượt quá 255 ký tự")
    private String beneficiaryName;

    @Size(max = 500, message = "Địa chỉ bên thụ hưởng không được vượt quá 500 ký tự")
    private String beneficiaryAddress;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@gmail\\.com$", message = "Email liên hệ phải có định dạng @gmail.com")
    @Size(max = 255, message = "Email liên hệ không được vượt quá 255 ký tự")
    private String contactEmail;

    @Pattern(regexp = "^0[0-9]{9,15}$", message = "Số điện thoại phải bắt đầu bằng số 0 và chứa từ 9 đến 15 chữ số")
    @NotBlank(message = "Số điện thoại không được để trống !")
    private String phoneNumber;

    private java.util.List<String> publicIds;
}