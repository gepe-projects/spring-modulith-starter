package com.gepe.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

@Modulith(sharedModules = "platform")
public class SpringModulithStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringModulithStarterApplication.class, args);
    }

}
