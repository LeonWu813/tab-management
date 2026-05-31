package com.tabvault.backend;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class TabVaultApplication {

    static {
        // Register BouncyCastle as a JCE provider.
        // Required by webpush-java (nl.martijndwars:web-push) for ECDH key agreement
        // used in VAPID-authenticated Web Push message encryption (MOD-005, AC-061).
        // Registered at class-load time so it is available before Spring context starts.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TabVaultApplication.class, args);
    }
}
