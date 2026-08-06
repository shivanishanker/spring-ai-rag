package com.example.springairag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringAiRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiRagApplication.class, args);
    }
}
