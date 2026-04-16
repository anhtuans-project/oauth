package com.example.oauthserver.dto;

import lombok.Data;

@Data
public class TokenRequest {
    private String grant_type;
    private String code;
    private String client_id;
    private String client_secret;
    private String redirect_uri;
    private String refresh_token;
}
