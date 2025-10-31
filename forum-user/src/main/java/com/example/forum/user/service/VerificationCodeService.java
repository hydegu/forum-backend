package com.example.forum.user.service;

import java.util.Optional;

public interface VerificationCodeService {
    void store(String email,String code);
    Optional<String> get(String email);
    void clear(String email);
    boolean acquireSendQuota(String email,String ip);
}
