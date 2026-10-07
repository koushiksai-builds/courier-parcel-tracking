package edu.vitap.common;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParcelEventConfiguration {
    public static final String EXCHANGE="parcel.events", FAILED_EXCHANGE="parcel.failed";
    @Bean TopicExchange parcelEventsExchange(){return new TopicExchange(EXCHANGE);}
    @Bean TopicExchange failedEventsExchange(){return new TopicExchange(FAILED_EXCHANGE);}
    @Bean Jackson2JsonMessageConverter parcelJsonConverter(){return new Jackson2JsonMessageConverter();}
}
