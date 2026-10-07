package edu.vitap.common;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class CommonSecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetailsService(UserRepository users){return email -> users.findByEmail(email).map(u -> User.withUsername(u.getEmail()).password(u.getPassword()).roles(u.getRole().name()).build()).orElseThrow(()->new UsernameNotFoundException("User not found"));}
    @Bean SecurityFilterChain serviceSecurity(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).httpBasic(Customizer.withDefaults()).build();}
}
