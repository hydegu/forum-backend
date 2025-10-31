package com.example.forum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Forum 网关应用启动类
 * Spring Cloud Gateway 统一入口
 *
 * @author Forum Team
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ForumGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ForumGatewayApplication.class, args);
	}

}
