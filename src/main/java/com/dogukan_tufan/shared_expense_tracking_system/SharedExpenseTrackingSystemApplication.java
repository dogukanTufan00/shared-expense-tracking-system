package com.dogukan_tufan.shared_expense_tracking_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on @Scheduled methods (the daily recurring-expense job).
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SharedExpenseTrackingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SharedExpenseTrackingSystemApplication.class, args);
	}

}
