package dev.sivalabs.helpdesk.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Messages are JSON, with the payload's class in the {@code __TypeId__} header so a listener gets the right
 * record back. Only the classes of published events (the tickets module) are trusted for that. Each module
 * declares its own exchanges and queues.
 */
@Configuration
class RabbitMQConfig {

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("dev.sivalabs.helpdesk.tickets");
    }
}
