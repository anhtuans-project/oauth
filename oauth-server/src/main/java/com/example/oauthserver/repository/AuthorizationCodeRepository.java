package com.example.oauthserver.repository;

import com.example.oauthserver.model.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, String> {}
