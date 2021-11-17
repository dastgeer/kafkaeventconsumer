package com.learnkafka.libraryeventsconsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LibraryEventsConsumer {

    @Autowired
    private LibraryEventsService libraryEventsService;

    @KafkaListener(topics = {"library-events"})
    public void onMessage(ConsumerRecord<Integer,String> consumerRecord) throws JsonProcessingException {
         System.out.println("consumerRecord--->"+consumerRecord);
        log.info("recieved records from kafka topic :{}",consumerRecord);
        libraryEventsService.saveLibraryEvents(consumerRecord);
    }
}
