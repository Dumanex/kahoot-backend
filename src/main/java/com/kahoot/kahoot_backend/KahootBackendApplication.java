package com.kahoot.kahoot_backend;

import com.kahoot.kahoot_backend.config.CorsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CorsProperties.class)
public class KahootBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KahootBackendApplication.class, args);
	}

}
