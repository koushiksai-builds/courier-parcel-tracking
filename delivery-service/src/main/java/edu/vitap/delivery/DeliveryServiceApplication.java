package edu.vitap.delivery;
import edu.vitap.common.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EntityScan(basePackages={"edu.vitap.common","edu.vitap.delivery"}) @EnableJpaRepositories(basePackages={"edu.vitap.common","edu.vitap.delivery"}) @Import({CommonSecurityConfiguration.class,ParcelEventConfiguration.class}) @EnableScheduling
public class DeliveryServiceApplication {public static void main(String[] args){SpringApplication.run(DeliveryServiceApplication.class,args);}}
