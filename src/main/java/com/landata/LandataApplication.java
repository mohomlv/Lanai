package com.landata;

import com.landata.shared.config.LandataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LandataProperties.class)
public class LandataApplication {
  public static void main(String[] args) {
    SpringApplication.run(LandataApplication.class, args);
  }
}
