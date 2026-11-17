package com.okit.commande;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsCommandeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCommandeApplication.class, args);
    }
}
