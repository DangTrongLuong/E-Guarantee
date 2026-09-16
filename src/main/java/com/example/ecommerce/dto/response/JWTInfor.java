package com.example.ecommerce.dto.response;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JWTInfor {
    private String id;
    private Date issueTime;
    private Date expirationTime;
    private String refreshTokenId;
    private String subject;
    private String type;

}

