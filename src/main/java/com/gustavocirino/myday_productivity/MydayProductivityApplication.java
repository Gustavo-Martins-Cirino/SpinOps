package com.gustavocirino.myday_productivity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MydayProductivityApplication {

    public static void main(String[] args) {
        SpringApplication.run(MydayProductivityApplication.class, args);
    }
}