package com.sachett.samosa.playground.repositories;

import com.sachett.samosa.playground.models.ProgramDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProgramRepository extends MongoRepository<ProgramDocument, String> { }
