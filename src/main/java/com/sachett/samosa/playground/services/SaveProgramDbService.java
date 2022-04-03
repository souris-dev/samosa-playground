package com.sachett.samosa.playground.services;

import com.sachett.samosa.playground.models.ProgramDocument;
import com.sachett.samosa.playground.repositories.ProgramRepository;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class SaveProgramDbService {

    @Autowired
    ProgramRepository programRepository;

    @Job(name = "Save to DB", retries = 3)
    public void executeSaveContentDb(File sourceFile,
                                     File outClassFile,
                                     String output, String error, String[] inputs,
                                     String sessionId) throws Exception {
        // Read the file contents
        // We actually can avoid doing this by passing the program as the argument to this function
        BufferedReader reader = new BufferedReader(new FileReader(sourceFile));
        StringWriter program = new StringWriter();
        PrintWriter programWriter = new PrintWriter(program);
        reader.lines().forEach(programWriter::println);
        programWriter.flush();
        reader.close();

        ProgramDocument programDocument = new ProgramDocument(
                program.toString(),
                output,
                error,
                inputs,
                sessionId
        );

        programRepository.insert(programDocument);

        // Delete the program file
        if (!sourceFile.delete()) {
            throw new Exception("Could not delete program file: " + sourceFile.getCanonicalPath());
        }

        // Delete the class file
        if (!outClassFile.delete()) {
            throw new Exception("Could not delete class file: " + outClassFile.getCanonicalPath());
        }
    }
}
