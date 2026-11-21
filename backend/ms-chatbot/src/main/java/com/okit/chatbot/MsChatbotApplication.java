package com.okit.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsChatbotApplication.class, args);
    }
}
