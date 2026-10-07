package edu.vitap.users;
import edu.vitap.common.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication @EntityScan(basePackages={"edu.vitap.common"}) @EnableJpaRepositories(basePackages={"edu.vitap.common"}) @Import(CommonSecurityConfiguration.class)
public class UserServiceApplication {
 public static void main(String[] args){SpringApplication.run(UserServiceApplication.class,args);}
 @Bean ApplicationRunner initialAdmin(UserRepository users,org.springframework.security.crypto.password.PasswordEncoder encoder){return args->{String email=System.getenv().getOrDefault("ADMIN_EMAIL","admin@courier.local").toLowerCase();if(users.findByEmail(email).isEmpty())users.save(new AppUser(email,encoder.encode(System.getenv().getOrDefault("ADMIN_PASSWORD","admin-change-me")),"Administrator",Role.ADMIN));};}
}
