package com.learnkafka.libraryeventsconsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.libraryeventsconsumer.entity.LibraryEvent;
import com.learnkafka.libraryeventsconsumer.repository.LibraryEventsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.Optional;

@Service
@Slf4j
public class LibraryEventsService {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LibraryEventsRepository libraryEventsRepository;

    public void saveLibraryEvents(ConsumerRecord<Integer,String> consumerRecord) throws JsonProcessingException {

        LibraryEvent libraryEventPayload= objectMapper.readValue(consumerRecord.value(), LibraryEvent.class);
        log.info("libraryEventPayload: service {}",libraryEventPayload);
        if(libraryEventPayload.getLibraryEventId()==0){
            throw new RecoverableDataAccessException("data connection exception");
        }
        switch (libraryEventPayload.getLibraryEventType()){
            case NEW:
                //create new event
                saveLibraryEvent(libraryEventPayload);
                break;

            case UPDATE:
                //update the existing event
                validateEvent(libraryEventPayload);
                saveLibraryEvent(libraryEventPayload);
                break;

             default:
                 log.info("no library event type found");
        }
    }

    private void validateEvent(LibraryEvent libraryEventPayload) {
        if(libraryEventPayload.getLibraryEventId()==null){
            throw new IllegalArgumentException("Library event id should not be null");
        }
        Optional<LibraryEvent> byId = libraryEventsRepository.findById(libraryEventPayload.getLibraryEventId());
        if(!byId.isPresent()){
            throw new IllegalArgumentException("No data found for id "+libraryEventPayload.getLibraryEventId());
        }
        log.info("library event validated successfully before update");
    }

    private void saveLibraryEvent(LibraryEvent libraryEventPayload) {
        libraryEventPayload.getBook().setLibraryEvent(libraryEventPayload);
        libraryEventsRepository.save(libraryEventPayload);
        log.info("library event is saving to db -->{}",libraryEventPayload);
    }

//this will call by recovery logic from ConcurrentKafkaListenerContainerFactory  which is call on retry if recoverable then this record will pass to same configurable
    //topic and get produced as prodcucer and will consume by same consumer until it consumed the record.
    public void handleRecovery(ConsumerRecord<Integer, String> consumerRecord) {
        Integer  key = consumerRecord.key();
        String message= consumerRecord.value();
        ListenableFuture<SendResult<Integer,String>> listenableFuture = kafkaTemplate.sendDefault(key,message);
        listenableFuture.addCallback(new ListenableFutureCallback<SendResult<Integer,String>>(){

            @Override
            public void onSuccess(SendResult<Integer, String> result) {
                handleSuccess(key,message,result);
            }

            @Override
            public void onFailure(Throwable ex) {
                handleFailure(key,message,ex);
            }
        });
    }

    private void handleSuccess(Integer key, String valueData, SendResult<Integer, String> result) {
        log.info(" message sent successfully for the key :{} value :{} partition :{}",key,valueData,result.getRecordMetadata().partition());
    }

    private void handleFailure(Integer key, String valueData, Throwable ex) {
        log.error(" message sent failed exception is ",ex.getMessage());
        try {
            throw ex;
        } catch (Throwable throwable) {
            log.error(" error on failure ",throwable.getMessage());
            throwable.printStackTrace();
        }
    }
}
