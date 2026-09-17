package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectGuaranteeRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(min = 10, max = 500, message = "Lý do từ chối phải có độ dài từ 10 đến 500 ký tự")
    private String reason;
}