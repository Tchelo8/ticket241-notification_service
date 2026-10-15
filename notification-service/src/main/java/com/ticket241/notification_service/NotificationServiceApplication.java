
package com.ticket241.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
//@EnableFeignClients
@EnableScheduling
public class NotificationServiceApplication {

	public static void main(String[] args) {

		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
