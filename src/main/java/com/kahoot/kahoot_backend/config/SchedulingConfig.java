package com.kahoot.kahoot_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Separate from the main class so @WebMvcTest/@DataJpaTest slices don't start the jobs
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
