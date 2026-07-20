package com.licensemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LicenseManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicenseManagementApplication.class, args);
    }
}
