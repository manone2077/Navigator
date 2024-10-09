package com.kk.navigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NavigatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NavigatorApplication.class, args);
    }

}
