package com.example.oauthserver.service;

import com.example.oauthserver.model.*;
import com.example.oauthserver.repository.*;
import com.example.oauthserver.util.TokenUtils;
import com.example.oauthserver.dto.TokenResponse;
import com.example.oauthserver.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private final ClientRepository clientRepo;
    private final UserRepository userRepo;
    private final AuthorizationCodeRepository authCodeRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final AccessTokenRepository accessTokenRepo;

    // Giả lập người dùng đã đăng nhập vào Auth Server (trong thực tế lấy từ SecurityContext)
    // Ở đây ta hardcode user ID 1 cho đơn giản, hoặc bạn cần một cơ chế login riêng cho Project 2
    public User getCurrentlyLoggedInUser() {
        return userRepo.findById(1L).orElseThrow(() -> new RuntimeException("User not logged in to Auth Server"));
    }

    /**
     * Bước 1 & 2: Xử lý yêu cầu /authorize
     * Trả về Authorization Code
     */
    @Transactional
    public String handleAuthorizationRequest(String clientId, String redirectUri, String state) {
        // 1. Validate Client
        Client client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid client_id"));

        if (!client.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("Mismatched redirect_uri");
        }

        // 2. Lấy user hiện tại (giả sử đã login vào trang admin/auth của Project 2 trước đó)
        User user = getCurrentlyLoggedInUser();

        // 3. Sinh Auth Code
        String code = TokenUtils.generateSecureToken(32);

        // 4. Lưu Code vào DB
        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(code);
        authCode.setUser(user);
        authCode.setClient(client);
        authCode.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // Hết hạn sau 10 phút
        authCode.setUsed(false);

        authCodeRepo.save(authCode);

        return code; // Trả về code để Controller redirect
    }

    /**
     * Bước 4 & 5: Đổi Code lấy Token
     */
    @Transactional
    public TokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri) {
        // 1. Validate Client Secret
        Client client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid client_id"));

        if (!client.getClientSecret().equals(clientSecret)) {
            throw new IllegalArgumentException("Invalid client_secret");
        }

        // 2. Tìm và validate Auth Code
        AuthorizationCode authCode = authCodeRepo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid authorization code"));

        if (authCode.isUsed()) {
            throw new IllegalArgumentException("Authorization code already used");
        }

        if (LocalDateTime.now().isAfter(authCode.getExpiresAt())) {
            throw new IllegalArgumentException("Authorization code expired");
        }

        if (!authCode.getClient().getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Client mismatch");
        }

        // Đánh dấu code đã dùng
        authCode.setUsed(true);
        authCodeRepo.save(authCode);

        // 3. Sinh Access Token (Opaque)
        String accessTokenValue = TokenUtils.generateSecureToken(32);
        AccessToken accessToken = new AccessToken();
        accessToken.setTokenValue(accessTokenValue);
        accessToken.setUser(authCode.getUser());
        accessToken.setClient(authCode.getClient());
        accessToken.setExpiresAt(LocalDateTime.now().plusHours(1)); // 1 giờ
        accessTokenRepo.save(accessToken);

        // 4. Sinh Refresh Token
        String refreshTokenValue = TokenUtils.generateSecureToken(64);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenValue(refreshTokenValue);
        refreshToken.setUser(authCode.getUser());
        refreshToken.setClient(authCode.getClient());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30)); // 30 ngày
        refreshToken.setRevoked(false);
        refreshTokenRepo.save(refreshToken);

        // 5. Trả về response
        TokenResponse response = new TokenResponse();
        response.setAccess_token(accessTokenValue);
        response.setRefresh_token(refreshTokenValue);
        response.setToken_type("Bearer");
        response.setExpires_in(3600);

        return response;
    }

    /**
     * Bước Refresh: Đổi Refresh Token lấy Access Token mới
     */
    @Transactional
    public TokenResponse refreshAccessToken(String refreshTokenValue, String clientId, String clientSecret) {
        // 1. Validate Client
        Client client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid client_id"));

        if (!client.getClientSecret().equals(clientSecret)) {
            throw new IllegalArgumentException("Invalid client_secret");
        }

        // 2. Tìm Refresh Token
        RefreshToken rt = refreshTokenRepo.findById(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (rt.isRevoked() || LocalDateTime.now().isAfter(rt.getExpiresAt())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        // 3. Rotation: Hủy token cũ, sinh token mới (Bảo mật cao)
        rt.setRevoked(true);
        refreshTokenRepo.save(rt);

        String newRefreshTokenValue = TokenUtils.generateSecureToken(64);
        RefreshToken newRt = new RefreshToken();
        newRt.setTokenValue(newRefreshTokenValue);
        newRt.setUser(rt.getUser());
        newRt.setClient(rt.getClient());
        newRt.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshTokenRepo.save(newRt);

        // 4. Sinh Access Token mới
        String newAccessTokenValue = TokenUtils.generateSecureToken(32);
        AccessToken newAt = new AccessToken();
        newAt.setTokenValue(newAccessTokenValue);
        newAt.setUser(rt.getUser());
        newAt.setClient(rt.getClient());
        newAt.setExpiresAt(LocalDateTime.now().plusHours(1));
        accessTokenRepo.save(newAt);

        TokenResponse response = new TokenResponse();
        response.setAccess_token(newAccessTokenValue);
        response.setRefresh_token(newRefreshTokenValue);
        response.setToken_type("Bearer");
        response.setExpires_in(3600);

        return response;
    }

    /**
     * Bước 6: Lấy thông tin User
     */
    public UserInfo getUserInfoByToken(String accessTokenValue) {
        AccessToken token = accessTokenRepo.findById(accessTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid access token"));

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new IllegalArgumentException("Access token expired");
        }

        User user = token.getUser();
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setEmail(user.getEmail());
        return info;
    }
}
