package com.example.oauthserver.dto;

import lombok.Data;

@Data
public class TokenResponse {
    private String access_token;
    private String refresh_token;
    private String token_type;
    private int expires_in;
}
