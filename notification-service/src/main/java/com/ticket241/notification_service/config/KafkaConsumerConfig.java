package com.ticket241.notification_service.config;


import com.ticket241.notification_service.event.TicketConfirmedEvent;
import com.ticket241.notification_service.event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    // 1 essai initial + 2 retries, espacés de 2s, avant abandon définitif du message
    private static final long RETRY_INTERVAL_MS = 2000L;
    private static final long MAX_RETRIES = 2L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // Configuration du ConsumerFactory pour désérialiser les messages JSON en UserRegisteredEvent
    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Le commit doit rester manuel : le listener gère lui-même l'Acknowledgment
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // false = ignorer les type headers éventuels du producteur, toujours désérialiser en UserRegisteredEvent
        JsonDeserializer<UserRegisteredEvent> valueDeserializer =
                new JsonDeserializer<>(UserRegisteredEvent.class, false);
        // Liste blanche restreinte au package des events plutôt que "*" (évite la désérialisation
        // arbitraire de classes si un message malveillant/corrompu atterrit sur le topic)
        valueDeserializer.addTrustedPackages("com.ticket241.notification_service.event");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
    }

    // Factory pour créer les listeners Kafka
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        // Requis pour que le paramètre Acknowledgment du @KafkaListener soit utilisable
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(kafkaErrorHandler(dlqKafkaTemplate()));

        return factory;
    }

    // Même principe pour les messages ticket-confirmed : toujours désérialisés en TicketConfirmedEvent
    @Bean
    public ConsumerFactory<String, TicketConfirmedEvent> ticketConfirmedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<TicketConfirmedEvent> valueDeserializer =
                new JsonDeserializer<>(TicketConfirmedEvent.class, false);
        valueDeserializer.addTrustedPackages("com.ticket241.notification_service.event");

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
    }

    // Factory des listeners de confirmation de billets : même ack manuel et même retry + DLT
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TicketConfirmedEvent> ticketConfirmedListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TicketConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(ticketConfirmedConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(kafkaErrorHandler(dlqKafkaTemplate()));

        return factory;
    }

    // ProducerFactory dédié à la republication des messages en échec vers leur topic .DLT
    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }

    // Retry borné : au-delà de MAX_RETRIES tentatives, le message est republié sur "<topic>.DLT"
    // (avec l'erreur en header) au lieu d'être rejoué indéfiniment ou simplement perdu après un log
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlqKafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> log.warn(
                "[Kafka] Tentative {} échouée — topic={}, partition={}, offset={} : {}",
                deliveryAttempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));

        return errorHandler;
    }
}