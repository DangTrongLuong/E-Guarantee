package com.example.ecommerce.dto.response;

import lombok.*;
import lombok.experimental.StandardException;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPayload {
    private String token;
    private Long timeToLive;
    private String jwtId;

}
