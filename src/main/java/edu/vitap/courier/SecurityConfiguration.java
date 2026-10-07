package edu.vitap.courier;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetailsService(UserRepository users){
        return email -> users.findByEmail(email).map(u -> User.withUsername(u.email).password(u.password).roles(u.role.name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
                .anyRequest().permitAll()).httpBasic(Customizer.withDefaults()).build();
    }
    @Bean ApplicationRunner seedAdmin(UserRepository users, PasswordEncoder encoder){
        return args -> { String email=System.getenv().getOrDefault("ADMIN_EMAIL","admin@courier.local");
            if(users.findByEmail(email).isEmpty()) users.save(new AppUser(email,encoder.encode(System.getenv().getOrDefault("ADMIN_PASSWORD","admin-change-me")),"Administrator",Role.ADMIN)); };
    }
}
