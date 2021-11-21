package com.learnkafka.libraryeventsconsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.libraryeventsconsumer.entity.LibraryEvent;
import com.learnkafka.libraryeventsconsumer.repository.LibraryEventsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class LibraryEventsService {

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
}
