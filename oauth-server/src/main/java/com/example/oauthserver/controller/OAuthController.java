package com.example.oauthserver.controller;

import com.example.oauthserver.dto.TokenResponse;
import com.example.oauthserver.dto.TokenRequest;
import com.example.oauthserver.dto.UserInfo;
import com.example.oauthserver.service.OAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    // Bước 1: Nhận yêu cầu ủy quyền
    @GetMapping("/authorize")
    public String authorize(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String state,
            @RequestParam String response_type, // Phải là 'code'
            @SessionAttribute(value = "user_id", required = false) Long userId) { // Giả lập user đã login

        // Lưu ý: Trong thực tế, nếu chưa login, phải redirect đến trang /login của Project 2 trước.
        // Ở đây giả sử userId đã tồn tại trong session hoặc context.

        try {
            // Gọi service để sinh code
            String code = oauthService.handleAuthorizationRequest(client_id, redirect_uri, state);

            // Redirect về Project 1 kèm code và state gốc
            String url = redirect_uri + "?code=" + code;
            if (state != null) {
                url += "&state=" + state;
            }
            return "redirect:" + url;
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // Bước 4: Đổi code lấy token
    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestBody TokenRequest request) {
        try {
            TokenResponse response;
            if ("authorization_code".equals(request.getGrant_type())) {
                response = oauthService.exchangeCodeForToken(
                        request.getCode(),
                        request.getClient_id(),
                        request.getClient_secret(),
                        request.getRedirect_uri()
                );
            } else if ("refresh_token".equals(request.getGrant_type())) {
                response = oauthService.refreshAccessToken(
                        request.getRefresh_token(),
                        request.getClient_id(),
                        request.getClient_secret()
                );
            } else {
                return ResponseEntity.badRequest().body("Unsupported grant type");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Bước 6: Lấy thông tin user
    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            UserInfo userInfo = oauthService.getUserInfoByToken(token);
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
    //(Lưu ý: Bạn cần thêm một LoginController đơn giản trong Project 2 để set userId vào session khi người dùng đăng nhập vào trang Auth Server, nếu không handleAuthorizationRequest sẽ lỗi.)
}

