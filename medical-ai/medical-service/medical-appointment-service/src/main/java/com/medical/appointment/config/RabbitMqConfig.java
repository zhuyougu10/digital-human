package com.medical.appointment.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "medical.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    private static final String APPOINTMENT_EVENT_PATTERN = "appointment.#";

    @Bean
    public TopicExchange medicalEventExchange(@Value("${medical.mq.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue appointmentNotificationQueue(
            @Value("${medical.mq.notification-queue}") String queueName,
            @Value("${medical.mq.exchange}") String exchangeName,
            @Value("${medical.mq.notification-dlq}") String deadLetterQueueName) {
        return new Queue(queueName, true, false, false, buildDeadLetterArgs(exchangeName, deadLetterQueueName));
    }

    @Bean
    public Queue appointmentNotificationDlq(@Value("${medical.mq.notification-dlq}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue appointmentMetricsQueue(
            @Value("${medical.mq.metrics-queue}") String queueName,
            @Value("${medical.mq.exchange}") String exchangeName,
            @Value("${medical.mq.metrics-dlq}") String deadLetterQueueName) {
        return new Queue(queueName, true, false, false, buildDeadLetterArgs(exchangeName, deadLetterQueueName));
    }

    @Bean
    public Queue appointmentMetricsDlq(@Value("${medical.mq.metrics-dlq}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue appointmentAuditQueue(
            @Value("${medical.mq.audit-queue}") String queueName,
            @Value("${medical.mq.exchange}") String exchangeName,
            @Value("${medical.mq.audit-dlq}") String deadLetterQueueName) {
        return new Queue(queueName, true, false, false, buildDeadLetterArgs(exchangeName, deadLetterQueueName));
    }

    @Bean
    public Queue appointmentAuditDlq(@Value("${medical.mq.audit-dlq}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding appointmentNotificationBinding(Queue appointmentNotificationQueue, TopicExchange medicalEventExchange) {
        return BindingBuilder.bind(appointmentNotificationQueue).to(medicalEventExchange).with(APPOINTMENT_EVENT_PATTERN);
    }

    @Bean
    public Binding appointmentNotificationDlqBinding(
            Queue appointmentNotificationDlq,
            TopicExchange medicalEventExchange,
            @Value("${medical.mq.notification-dlq}") String deadLetterQueueName) {
        return BindingBuilder.bind(appointmentNotificationDlq).to(medicalEventExchange).with(deadLetterQueueName);
    }

    @Bean
    public Binding appointmentMetricsBinding(Queue appointmentMetricsQueue, TopicExchange medicalEventExchange) {
        return BindingBuilder.bind(appointmentMetricsQueue).to(medicalEventExchange).with(APPOINTMENT_EVENT_PATTERN);
    }

    @Bean
    public Binding appointmentMetricsDlqBinding(
            Queue appointmentMetricsDlq,
            TopicExchange medicalEventExchange,
            @Value("${medical.mq.metrics-dlq}") String deadLetterQueueName) {
        return BindingBuilder.bind(appointmentMetricsDlq).to(medicalEventExchange).with(deadLetterQueueName);
    }

    @Bean
    public Binding appointmentAuditBinding(Queue appointmentAuditQueue, TopicExchange medicalEventExchange) {
        return BindingBuilder.bind(appointmentAuditQueue).to(medicalEventExchange).with(APPOINTMENT_EVENT_PATTERN);
    }

    @Bean
    public Binding appointmentAuditDlqBinding(
            Queue appointmentAuditDlq,
            TopicExchange medicalEventExchange,
            @Value("${medical.mq.audit-dlq}") String deadLetterQueueName) {
        return BindingBuilder.bind(appointmentAuditDlq).to(medicalEventExchange).with(deadLetterQueueName);
    }

    private Map<String, Object> buildDeadLetterArgs(String exchangeName, String deadLetterRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exchangeName);
        args.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        return args;
    }
}
