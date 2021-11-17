package com.learnkafka.libraryeventsconsumer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Book {
    @Id
    private Integer bookId;
    private String bookName;
    private String authorName;
    @OneToOne
    @JoinColumn(name="libraryEventIdRelation")
    private LibraryEvent libraryEvent;

}
