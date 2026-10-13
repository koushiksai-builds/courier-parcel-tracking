package edu.vitap.app;

import edu.vitap.common.AppUser;
import edu.vitap.common.Role;
import edu.vitap.common.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "edu.vitap")
@EntityScan(basePackages = "edu.vitap")
@EnableJpaRepositories(basePackages = "edu.vitap")
@EnableScheduling
public class CourierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierApplication.class, args);
    }

    @Bean
    ApplicationRunner initialAdmin(UserRepository users, PasswordEncoder encoder,
                                   @Value("${admin.email:admin@courier.local}") String email,
                                   @Value("${admin.password:admin-change-me}") String password) {
        return args -> {
            String normalized = email.toLowerCase();
            if (users.findByEmail(normalized).isEmpty()) {
                users.save(new AppUser(normalized, encoder.encode(password), "Administrator", Role.ADMIN));
            }
        };
    }
}
