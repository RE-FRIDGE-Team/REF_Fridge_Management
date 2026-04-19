package com.refridge.fridge_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling  // 유통기한 임박 배치 스케줄러
public class FridgeManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FridgeManagementApplication.class, args);
    }

}
