package com.courier.userservice.config;

import com.courier.userservice.model.Role;
import com.courier.userservice.model.User;
import com.courier.userservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initAdmin(
            UserRepository repository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (!repository.existsByEmail("admin@courier.com")) {

                User admin = new User();
                admin.setName("System Admin");
                admin.setEmail("admin@courier.com");
                admin.setPassword(
                        passwordEncoder.encode("Admin@123")
                );
                admin.setRole(Role.ADMIN);

                repository.save(admin);

                System.out.println("Default admin created");
            }
        };
    }
}