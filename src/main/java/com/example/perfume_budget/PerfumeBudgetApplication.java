package com.example.perfume_budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PerfumeBudgetApplication {

	public static void main(String[] args) {
		SpringApplication.run(PerfumeBudgetApplication.class, args);
	}

}
