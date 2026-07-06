package com.example.demo.infra.adapter;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.MessagePublisherPort;
import com.example.demo.application.shared.command.outbound.PublishEventCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessagePublisherAdapter implements MessagePublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void send(PublishEventCommand command) {
        log.trace("Kafka Adapter preparing to send message to topic [{}], key [{}]",
                command.topic(), command.routingKey());

        // 解開 Command 並發動非同步網路傳輸
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(command.topic(), command.routingKey(), command.eventJson());

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Kafka ACK received successfully. Topic: [{}]", result.getRecordMetadata().topic());
            } else {
                log.error("Kafka Producer critical error! Failed to deliver message to topic [{}], key [{}].",
                        command.topic(), command.routingKey(), ex);
            }
        });
    }
}