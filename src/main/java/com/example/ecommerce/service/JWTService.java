package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.JWTInfor;
import com.example.ecommerce.dto.response.TokenPayload;
import com.example.ecommerce.entity.AccessTokenBlackList;
import com.example.ecommerce.entity.RefreshTokenWhiteList;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.repository.AccessTokenBlackListRepository;
import com.example.ecommerce.repository.RefreshTokenWhiteListRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JWTService {
    private final RefreshTokenWhiteListRepository refreshTokenWhiteListRepository;
    private final AccessTokenBlackListRepository accessTokenBlackListRepository;
    private final String refreshId = "refreshJwtId";
    private final String CLAIM_TYPE = "type";
    private final String TYPE_ACCESS = "access";
    private final String TYPE_REFRESH = "refresh";


    @Value("${secretkey}")
    private String secretKey;

    public TokenPayload generateAccessToken(User user, String refreshJwtId){
        if (user.getRole() == null) {
            throw new IllegalStateException("User role is required to generate access token");
        }
        log.info("Generating access token for username={}", user.getUsername());
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        Date issueTime = new Date();
        String jwtId = UUID.randomUUID().toString();
        Date expirationTime = Date.from(issueTime.toInstant().plus(15, ChronoUnit.MINUTES));
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .jwtID(jwtId)
                .subject(user.getUsername())
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .claim(refreshId, refreshJwtId)
                .claim("roles", List.of(user.getRole().name()))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .build();
        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(secretKey));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
        String token = jwsObject.serialize();
        TokenPayload tokenPayload = TokenPayload.builder()
                .jwtId(jwtId)
                .token(token)
                .timeToLive(expirationTime.getTime() - new Date().getTime())
                .build();
        return tokenPayload;
    }

    public TokenPayload generateRefreshToken(User user){
        log.info("Generating refresh token for username={}", user.getUsername());
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        Date issueTime = new Date();
        String jwtId = UUID.randomUUID().toString();
        Date expirationTime = Date.from(issueTime.toInstant().plus(14, ChronoUnit.DAYS));
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .jwtID(jwtId)
                .subject(user.getUsername())
                .issueTime(issueTime)
                .expirationTime(expirationTime)
                .expirationTime(expirationTime)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .build();
        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(secretKey));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
        String token = jwsObject.serialize();
        TokenPayload tokenPayload = TokenPayload.builder()
                .jwtId(jwtId)
                .token(token)
                .timeToLive(expirationTime.getTime() - new Date().getTime())
                .build();
        return tokenPayload;
    }
    public boolean verifyAccessToken(String token) throws ParseException, JOSEException {
        log.info("Verifying access token");
        return verifyToken(token, TYPE_ACCESS);
    }
    public boolean verifyRefreshToken(String token) throws ParseException, JOSEException {
        log.info("Verifying refresh token");
        return verifyToken(token, TYPE_REFRESH);
    }


    public boolean verifyToken(String token, String accessType) throws ParseException, JOSEException {
        log.info("Verifying token type={}", accessType);
        if(token == null || token.isEmpty()){
            return false;
        }
        SignedJWT signedJWT = SignedJWT.parse(token);
        if(!signedJWT.verify(new MACVerifier(secretKey))){
            return false;
        }
        JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
        Date expirationTime = jwtClaimsSet.getExpirationTime();
        if(expirationTime == null || expirationTime.before(new Date())){
            return false;
        }
        String type = jwtClaimsSet.getStringClaim(CLAIM_TYPE);
        if(!type.equals(accessType)){
            return false;
        }

        String jwtId = jwtClaimsSet.getJWTID();
        if(TYPE_ACCESS.equals(accessType)) {
            Optional<AccessTokenBlackList> byId = accessTokenBlackListRepository.findById(jwtId);
            if (byId.isPresent()) {
                return false;
            }
        }
        else {
            Optional<RefreshTokenWhiteList> byId = refreshTokenWhiteListRepository.findById(jwtId);
            if (byId.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public JWTInfor parse(String token) throws ParseException {
        JWTClaimsSet jwtClaimsSet = SignedJWT.parse(token).getJWTClaimsSet();
        String jwtId = jwtClaimsSet.getJWTID();
        Date issueTime = jwtClaimsSet.getIssueTime();
        Date expirationTime = jwtClaimsSet.getExpirationTime();
        String refreshJwtId = jwtClaimsSet.getStringClaim(refreshId);
        String subject = jwtClaimsSet.getSubject();
        String type = jwtClaimsSet.getStringClaim(CLAIM_TYPE);

        return JWTInfor.builder()
                .id(jwtId)
                .issueTime(issueTime)
                .refreshTokenId(refreshJwtId)
                .type(type)
                .subject(subject)
                .expirationTime(expirationTime)
                .build();
    }

}
