package edu.vitap.payments;

import edu.vitap.common.CommonSecurityConfiguration;
import edu.vitap.common.ParcelEventConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages={"edu.vitap.common","edu.vitap.payments"})
@EnableJpaRepositories(basePackages={"edu.vitap.common","edu.vitap.payments"})
@Import({CommonSecurityConfiguration.class,ParcelEventConfiguration.class})
public class PaymentServiceApplication {
    public static void main(String[] args) { SpringApplication.run(PaymentServiceApplication.class, args); }
}
