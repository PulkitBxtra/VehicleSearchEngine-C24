package com.c24.vehiclesearch;

import org.springframework.boot.SpringApplication;

public class TestVehicleSearchApplication {

	public static void main(String[] args) {
		SpringApplication.from(VehicleSearchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
