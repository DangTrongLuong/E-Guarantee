package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.LoginRequest;
import com.example.ecommerce.dto.request.RefreshTokenRequest;
import com.example.ecommerce.dto.response.JWTInfor;
import com.example.ecommerce.dto.response.LoginResponse;
import com.example.ecommerce.dto.response.TokenPayload;
import com.example.ecommerce.entity.AccessTokenBlackList;
import com.example.ecommerce.entity.RefreshTokenWhiteList;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.repository.AccessTokenBlackListRepository;
import com.example.ecommerce.repository.RefreshTokenWhiteListRepository;
import com.example.ecommerce.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenWhiteListRepository refreshTokenWhiteListRepository;
    private final AccessTokenBlackListRepository accessTokenBlackListRepository;

    private final JWTService jwtService;

    public LoginResponse login(LoginRequest request){
        log.info("Attempting login for username={}", request.getUsername());
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        User user = (User) authenticate.getPrincipal();
        log.info("Authentication success for username={}", user.getUsername());
        return issueTokens(user);
    }

    public void logout(String token) throws ParseException {
        log.info("Attempting logout");
        JWTInfor jwtInfor = jwtService.parse(token);
        String jwtId = jwtInfor.getId();
        Date issueTime = jwtInfor.getIssueTime();
        Date expirationTime = jwtInfor.getExpirationTime();
        if(expirationTime.before(new Date())) return;
        AccessTokenBlackList accessTokenBlackList = AccessTokenBlackList.builder()
                .jwtId(jwtId)
                .timeToLive(expirationTime.getTime() - new Date().getTime())
                .build();
        accessTokenBlackListRepository.save(accessTokenBlackList);
        String refreshJwtId = jwtInfor.getRefreshTokenId();
        if(refreshJwtId != null && refreshTokenWhiteListRepository.existsById(refreshJwtId)){
            refreshTokenWhiteListRepository.deleteById(refreshJwtId);
        }
        log.info("Logout success for username={}", jwtInfor.getSubject());

    }

    public LoginResponse refresh(RefreshTokenRequest request) throws ParseException, JOSEException {
        log.info("Attempting refresh token");
        String oldRefreshToken = request.getRefreshToken();
        if(oldRefreshToken == null || !jwtService.verifyRefreshToken(oldRefreshToken)){
            throw new BadCredentialsException("Invalid refresh token!");
        }
        JWTInfor jwtInfor = jwtService.parse(oldRefreshToken);
        User user = userRepository.findByUsername(jwtInfor.getSubject()).orElseThrow(() -> new BadCredentialsException("User not found!"));
        refreshTokenWhiteListRepository.deleteById(jwtInfor.getId());
        log.info("Refresh token success for username={}", user.getUsername());
        return issueTokens(user);
    }

    public LoginResponse issueTokens(User user){
        log.info("Issuing tokens for username={}", user.getUsername());

        TokenPayload refreshTokenPayload = jwtService.generateRefreshToken(user);
        TokenPayload accessTokenPayload = jwtService.generateAccessToken(user, refreshTokenPayload.getJwtId());

        RefreshTokenWhiteList refreshTokenWhiteList = RefreshTokenWhiteList.builder()
                .jwtId(refreshTokenPayload.getJwtId())
                .timeToLive(refreshTokenPayload.getTimeToLive())
                .build();
        refreshTokenWhiteListRepository.save(refreshTokenWhiteList);
        return LoginResponse.builder()
                .accessToken(accessTokenPayload.getToken())
                .refreshToken(refreshTokenPayload.getToken())
                .build();
    }


}
