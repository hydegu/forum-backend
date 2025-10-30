package com.example.forum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService{
    private final RedisTemplate<String,Object> redisTemplate;
    //注意PT5M为一个默认值，表示5分钟
    @Value("${forum.cache.verification-code-ttl:PT5M}")
    private Duration codeTtl;
    @Value("${forum.cache.send-limit-window:PT10M}")
    private Duration window;
    @Value("${forum.cache.send-limit-max}")
    private int maxSend;

    private String codeKey(String email){ return "auth:code:" + email; }
    private String quotaKey(String email,String ip){ return "auth:quota:" + email + ":" + ip; }

    @Override
    public void store(String email, String code) {
        redisTemplate.opsForValue().set(codeKey(email),code,codeTtl);
    }

    @Override
    public Optional<String> get(String email) {
        return Optional.ofNullable((String) redisTemplate.opsForValue().get(codeKey(email)));
    }

    @Override
    public void clear(String email) {
        redisTemplate.delete(codeKey(email));
    }

    @Override
    public boolean acquireSendQuota(String email, String ip) {
        String key = quotaKey(email,ip);
        Long used = redisTemplate.opsForValue().increment(key);
        if(used != null && used == 1){
            redisTemplate.expire(key,window);
        }
        return used != null && used <= maxSend;
    }
}
