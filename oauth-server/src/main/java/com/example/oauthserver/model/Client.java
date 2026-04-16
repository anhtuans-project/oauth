package com.example.oauthserver.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "oauth_clients")
public class Client {
    @Id
    private String clientId; // Ví dụ: "project_1_client"

    @Column(nullable = false)
    private String clientSecret; // Ví dụ: "secret123"

    @Column(nullable = false)
    private String redirectUri; // Ví dụ: "http://localhost:8081/callback"
}