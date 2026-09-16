package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.Action;
import com.example.ecommerce.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingHistoryResponse {
    private Long id;
    private String guaranteeId;
    private Action action;
    private String performedBy;
    private String performedByFullName;
    private Role role;
    private LocalDateTime timestamp;
    private String comment;
}
