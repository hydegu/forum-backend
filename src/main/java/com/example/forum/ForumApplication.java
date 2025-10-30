package com.example.forum;

import com.example.forum.config.UploadProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.example.forum.repo")
@SpringBootApplication
@EnableConfigurationProperties(UploadProperties.class)
@EnableScheduling
public class ForumApplication {

    public static void main(String[] args) {

        SpringApplication.run(ForumApplication.class, args);
        
    }

}
