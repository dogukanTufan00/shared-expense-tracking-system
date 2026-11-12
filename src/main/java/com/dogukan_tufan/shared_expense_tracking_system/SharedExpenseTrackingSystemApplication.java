package com.dogukan_tufan.shared_expense_tracking_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Locale;

// @EnableScheduling turns on @Scheduled methods (the daily recurring-expense job).
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SharedExpenseTrackingSystemApplication {

	public static void main(String[] args) {
		// The "Turkish I" problem: on a Turkish computer Java's default locale is tr, where "HIGH".toLowerCase()
		// becomes "hıgh" (dotless ı). Libraries such as the Firebase SDK lowercase enum names that way, and
		// Firebase then rejects the message. A neutral default locale avoids this whole class of bugs; our own
		// code asks for Turkish formatting explicitly where it needs it (e.g. PushTextBuilder).
		Locale.setDefault(Locale.ROOT);
		SpringApplication.run(SharedExpenseTrackingSystemApplication.class, args);
	}

}
