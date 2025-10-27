package com.wzz.smscode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SmsCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsCodeApplication.class, args);
    }

}
