package com.example.SecureVault;

import org.springframework.boot.SpringApplication;

public class TestSecureVaultApplication {

	public static void main(String[] args) {
		SpringApplication.from(SecureVaultApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
