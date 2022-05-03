package com.sachett.samosa.playground.services;

import com.sachett.samosa.playground.models.ProgramDocument;
import com.sachett.samosa.playground.repositories.ProgramRepository;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

@Service
public class SaveProgramDbService {

    @Autowired
    ProgramRepository programRepository;

    /**
     * Saves the program to MongoDB collection and deletes the generated program and class file.
     * @param sourceFile    The source file
     * @param outClassFile  The generated output class file
     * @param output        The output of the program run
     * @param error         The error output of the program run
     * @param inputs        The inputs given to the program
     * @param sessionId     The session ID in which it was run
     * @throws Exception    If file(s) could not be deleted, throw Exception.
     */
    @Job(name = "Save to DB", retries = 3)
    public void executeSaveContentDb(File sourceFile,
                                     File outClassFile,
                                     String output, String error, List<String> inputs,
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
        if (outClassFile.exists() && !outClassFile.delete()) {
            throw new Exception("Could not delete generated class file: " + outClassFile.getCanonicalPath());
        }
    }
}
