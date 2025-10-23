package com.wzz.smscode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmsCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsCodeApplication.class, args);
    }

}
