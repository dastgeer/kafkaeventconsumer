package com.learnkafka.libraryeventsconsumer.config;

import com.learnkafka.libraryeventsconsumer.LibraryEventsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
@EnableKafka
public class LibraryEventsConsumerConfig {

    @Autowired
    private LibraryEventsService libraryEventsService;

    @Bean
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> kafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setConcurrency(3);
        //factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        //loggingErrorHandler is default implement to print error
        factory.setErrorHandler((thrownException,record)->{
            // this is custom logging if we want to perform some task like saving into db on error or some retry mechanism later on error record  we can do it other let kafka use its default one.
            log.info("exception in consumer config is {} and record is {}",thrownException,record);
        });
        //this will help in implement the retry operation.but retry operation only can help in connection issue or some temprory issue not with actual exception
        //if wew ill throw exception 100 time it will behave 100 times same way after 100 retry.
        factory.setRetryTemplate(simpleRetryTemplate());
        //here to set recovery callback and pass the logic or method implemention it will have recover method and take retrycontext  which mean it will take
        //data from retry operation when it will be go for retry.if it is recoverable then go for further retry if logic check if it is not retryable then it will go
        //throw exception and go to kafka error handler.
        factory.setRecoveryCallback((context)->{
            if(context.getLastThrowable() instanceof  RecoverableDataAccessException){
                // write recovery logic here
                log.info("recoverable logic block in retry callback");
                Arrays.asList(context.attributeNames()).forEach(attributeName -> System.out.println("printing all attributes  available in retrycontext"+attributeName));
                ConsumerRecord<Integer,String> consumerRecord = (ConsumerRecord<Integer,String>)context.getAttribute("record");
                //this will call by recovery logic from ConcurrentKafkaListenerContainerFactory  which is call on retry if recoverable then this record will pass to same configurable
                //topic and get produced as prodcucer and will consume by same consumer until it consumed the record.
                libraryEventsService.handleRecovery(consumerRecord);
            }else{
                log.info("non recoverable logic block in retry callback");
                throw new RuntimeException(context.getLastThrowable().getMessage());
            }
            return null;
        });
        return factory;
    }

    private RetryTemplate simpleRetryTemplate() {
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(1000);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(simpleRetryPolicy());
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        return retryTemplate;
    }

    private RetryPolicy simpleRetryPolicy(){
        // this ma[pping is used to map or allow to retry based on true false flag with corresponding exceptiin. if want to retry with some specific excpetion
        //then we will map with specific mapping with true otherwise false.
        Map<Class<? extends Throwable>,Boolean> exceptionRetryMapping = new HashMap();
        exceptionRetryMapping.put(IllegalArgumentException.class,false);
        exceptionRetryMapping.put(RecoverableDataAccessException.class,true);
        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(3,exceptionRetryMapping,true);
        return simpleRetryPolicy;
    }
}
