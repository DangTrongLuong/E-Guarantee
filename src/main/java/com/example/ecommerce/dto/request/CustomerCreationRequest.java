package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerCreationRequest {

    @Pattern(regexp = "^[0-9]{6,12}$", message = "CIF phải là chuỗi số từ 6 đến 12 chữ số")
    @NotBlank(message = "Mã CIF không được để trống!")
    @Size(min = 6, max = 12, message = "Mã CIF phải nằm trong khoảng 6-12 kí tự !")
    private String cif;

    @NotBlank(message = "Tên khách hàng không được để trống!")
    @Size(max = 255, message = "Tên khách hàng quá dài, vui lòng thử lại!")
    private String customerName;

    @Size(max = 20, message = "Mã số thuế tối đa 20 ký tự !")
    @NotBlank(message = "Mã số thuế không được để trống !")
    private String taxCode;

    @Size(max = 300, message = "Địa chỉ không được quá 300 kí tự!")
    private String address;


}
