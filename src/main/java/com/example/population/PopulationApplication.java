package com.example.population;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.population.mapper")
public class PopulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PopulationApplication.class, args);
    }
}