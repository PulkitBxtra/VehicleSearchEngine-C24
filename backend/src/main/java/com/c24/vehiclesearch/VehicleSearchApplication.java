package com.c24.vehiclesearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VehicleSearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(VehicleSearchApplication.class, args);
	}

}
