package com.sachett.samosa.playground.controllers;

import com.sachett.samosa.playground.models.FinishedResponse;
import com.sachett.samosa.playground.models.RunRequest;
import com.sachett.samosa.playground.models.RunResponse;
import com.sachett.samosa.playground.services.SaveProgramDbService;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Controller
public class ProgramRunController {

    // TODO: make these config vars
    private static final String compilerExecutablePath =
            System.getenv("ON_BEANSTALK") == null ? "./src/main/resources/binaries/samosac-1.0-SNAPSHOT-full.jar"
                                                        : "./compiler/samosac-1.0-SNAPSHOT-full.jar";

    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    protected JobScheduler jobScheduler;

    @Autowired
    protected SaveProgramDbService saveProgramDbService;

    ConcurrentHashMap<String, Thread> interactors = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, ConcurrentLinkedDeque<String>> interactorQueues = new ConcurrentHashMap<>();

    public void sendRunResponse(RunResponse runResponse, String sessionId) {
        simpMessagingTemplate.convertAndSend("/topic/run/" + sessionId,
                runResponse
        );
    }

    public void sendProgramFinishedResponse(FinishedResponse response, String sessionId) {
        simpMessagingTemplate.convertAndSend("/topic/finished/" + sessionId,
                response
        );
    }

    @MessageMapping("/run/{sessionId}")
    public RunResponse greeting(@DestinationVariable String sessionId, RunRequest message) throws Exception {
        // Run a new program:
        if (!message.getProgram().equals("") && !message.isHasInput()) {
            ProgramRunner programRunner = new ProgramRunner(this, sessionId,
                    message.getProgram(), jobScheduler, saveProgramDbService);
            Thread programRunnerThread = new Thread(programRunner);
            interactors.put(sessionId, programRunnerThread);
            interactorQueues.put(sessionId, programRunner.getMessageQ());
            programRunnerThread.start();
        }
        else if (message.isEndRun()) {
            interactors.get(sessionId).interrupt();
        }

        // Providing inputs to an already running program
        else if (message.isHasInput()) {
            var messageQ = interactorQueues.get(sessionId);
            messageQ.add(message.getInput());
        }

        return null;
    }

    static class ProgramRunner implements Runnable {
        private final ProgramRunController controller;
        private final String sessionId;
        private final String theProgram;

        private final ConcurrentLinkedDeque<String> messageQ;

        private final JobScheduler jobScheduler;
        private final SaveProgramDbService saveProgramDbService;

        public ProgramRunner(ProgramRunController controller,
                             String sessionId,
                             String program,
                             JobScheduler jobScheduler,
                             SaveProgramDbService saveProgramDbService) {
            this.controller = controller;
            this.sessionId = sessionId;
            this.theProgram = program;
            this.jobScheduler = jobScheduler;
            this.saveProgramDbService = saveProgramDbService;

            messageQ = new ConcurrentLinkedDeque<String>();
        }

        public ConcurrentLinkedDeque<String> getMessageQ() {
            return messageQ;
        }

        interface KillEffect {
            void onKill();
        }

        /**
         * Checks if passed thread is interrupted. If it is interrupted, executes
         * the killEffect.onKill() method.
         */
        static class RunKillPoller implements Runnable {
            Thread parentThread;
            KillEffect killEffect;

            public RunKillPoller(Thread parentThread, KillEffect killEffect) {
                this.parentThread = parentThread;
                this.killEffect = killEffect;
            }

            public void run() {
                while (!parentThread.isInterrupted() && !Thread.interrupted()) {}
                if (!(Thread.interrupted() && !parentThread.isInterrupted())) {
                    killEffect.onKill();
                }
            }
        }

        public void run() {
            try {
                final String sourceFile = "./playground-sources/source" + sessionId + ".samo";
                new File("./playground-sources").mkdirs();
                new File("./out").mkdirs();
                File sourceFileFile = new File(sourceFile);
                sourceFileFile.createNewFile();

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                System.out.println("----------------- Using file " + sourceFile); // debug
                System.out.println("----------------- Time: " + dtf.format(now));

                FileWriter sourceFileWriter = new FileWriter(sourceFileFile);
                sourceFileWriter.write(theProgram);
                sourceFileWriter.close();

                Process compileProcess = new ProcessBuilder().command("java", "-jar", compilerExecutablePath, sourceFile).start();
                InputStream compilationResultStream = compileProcess.getInputStream();
                InputStream compilationErrorStream = compileProcess.getErrorStream();

                StringWriter compilationResultWriter = new StringWriter();
                StringWriter compilationErrorWriter = new StringWriter();

                BufferedReader compilationOutReader = new BufferedReader(new InputStreamReader(compilationResultStream));
                BufferedReader compilationErrReader = new BufferedReader(new InputStreamReader(compilationErrorStream));

                Thread compilationResultOutStreamPoll = new Thread(() -> {
                    try {
                        int outChar;
                        while (!Thread.interrupted()
                                && (outChar = compilationOutReader.read()) != -1
                                && compileProcess.isAlive()) {
                            char outputChar = (char) outChar;
                            compilationResultWriter.write(outputChar);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                Thread compilationErrorOutStreamPoll = new Thread(() -> {
                    try {
                        int outChar;
                        while (!Thread.interrupted()
                                && (outChar = compilationErrReader.read()) != -1
                                && compileProcess.isAlive()) {
                            char outputChar = (char) outChar;
                            compilationErrorWriter.write(outputChar);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                compilationResultOutStreamPoll.start();
                compilationErrorOutStreamPoll.start();

                boolean compileTimeout = !compileProcess.waitFor(350, TimeUnit.SECONDS);
                System.out.println("Compile timeout? " + compileTimeout);

                // debug
                now = LocalDateTime.now();
                System.out.println("------- Time: " + dtf.format(now));

                String compilationResult = compilationResultWriter.toString();
                String compilationError = compilationErrorWriter.toString();

                System.out.println(compilationResult);

                if (!compilationError.equals("")) {
                    controller.sendRunResponse(new RunResponse(compilationResult, (compileTimeout ?
                            "Compilation timed out with error: " : "") + compilationError, true), sessionId);

                    controller.sendProgramFinishedResponse(
                            new FinishedResponse(true, "<b style=\"color:red\">Compilation failed.</b>"), sessionId
                    );
                    // if compilation failed, do not run the program
                    return;
                }
                else if (compileTimeout) {
                    controller.sendRunResponse(new RunResponse(compilationResult, "Compilation timed out.", true), sessionId);

                    controller.sendProgramFinishedResponse(
                            new FinishedResponse(true, "<b style=\"color:red\">Compilation failed.</b>"), sessionId
                    );
                    // if compilation failed, do not run the program
                    return;
                }

                System.out.println("--------- Compilation finished successfully. "); //debug

                // Compilation success.
                // Now run it.

                ProcessBuilder processBuilder = new ProcessBuilder("java", "Source" + sessionId + "Samo");
                processBuilder.directory(new File("./out"));
                final Process runProcess = processBuilder.start();;
                AtomicBoolean processExited = new AtomicBoolean(false);

                // Start the kill poller:
                RunKillPoller runKillPoller = new RunKillPoller(Thread.currentThread(), () -> {
                   if (runProcess.isAlive()) {
                       runProcess.destroyForcibly();
                   }
                });
                Thread runKillPollThread = new Thread(runKillPoller);
                runKillPollThread.start();

                // To maintain records:
                StringBuilder runOutput = new StringBuilder();
                StringBuilder runError = new StringBuilder();
                ArrayList<String> inputs = new ArrayList<>();

                InputStream runResultStream = runProcess.getInputStream();
                OutputStream runInputStream = runProcess.getOutputStream(); // input for the process, output for us
                InputStream runErrorStream = runProcess.getErrorStream();

                BufferedReader outReader = new BufferedReader(new InputStreamReader(runResultStream));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(runErrorStream));

                Thread resultOutStreamPoll = new Thread(() -> {
                    try {
                        int outChar;
                        StringWriter stringWriter = new StringWriter();
                        while (!Thread.interrupted() && (outChar = outReader.read()) != -1 && !processExited.get()) {
                            char outputChar = (char) outChar;
                            stringWriter.write(outputChar);
                            if (outputChar == '\n') {
                                controller.sendRunResponse(
                                        new RunResponse(stringWriter.toString(), "", false, false), sessionId
                                );
                                // clear the buffer:
                                stringWriter.getBuffer().setLength(0);
                            }

                            runOutput.append((char) outChar);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                Thread resultErrorStreamPoll = new Thread(() -> {
                    try {
                        int outChar;
                        StringWriter stringWriter = new StringWriter();
                        while (!Thread.interrupted() && (outChar = errReader.read()) != -1 && !processExited.get()) {
                            char outputChar = (char) outChar;
                            stringWriter.write(outputChar);
                            if (outputChar == '\n') {
                                controller.sendRunResponse(
                                        new RunResponse("", stringWriter.toString(), true, false), sessionId
                                );
                                // clear the buffer:
                                stringWriter.getBuffer().setLength(0);
                            }

                            runError.append((char) outChar);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                BufferedWriter processInputBufferedWriter = new BufferedWriter(new OutputStreamWriter(runInputStream));

                Thread resultInputStreamPoll = new Thread(() -> {
                    while (!Thread.interrupted() && !processExited.get()) {
                        String input = messageQ.poll();

                        if (input == null) {
                            continue;
                        }

                        try {
                            inputs.add(input);
                            processInputBufferedWriter.write(input);
                            processInputBufferedWriter.newLine();
                            processInputBufferedWriter.flush();
                        }
                        catch (IOException e) {
                            System.out.print("[sessionId: " + sessionId + "]");
                            e.printStackTrace(System.err);
                        }
                    }
                });

                Thread processChecker = new Thread() {
                    public void run() {
                        while (runProcess.isAlive()) {
                        }
                        resultOutStreamPoll.interrupt();
                        resultErrorStreamPoll.interrupt();
                        resultInputStreamPoll.interrupt();
                        processExited.set(true);
                        controller.sendProgramFinishedResponse(
                                new FinishedResponse(true), sessionId
                        );
                    }
                };

                resultOutStreamPoll.start();
                resultErrorStreamPoll.start();
                resultInputStreamPoll.start();
                processChecker.start();

                processChecker.join(180000);
                if (runProcess.isAlive()) {
                    System.out.println("Stopping child process by force.");
                    runProcess.destroyForcibly();
                    controller.sendProgramFinishedResponse(
                            new FinishedResponse(true), sessionId
                    );
                }
                processExited.set(true);
                runKillPollThread.interrupt();
                resultOutStreamPoll.join(1000);
                resultErrorStreamPoll.join(1000);
                resultInputStreamPoll.join(1000);
                runKillPollThread.join(1000);

                jobScheduler.enqueue(() -> saveProgramDbService.executeSaveContentDb(
                        sourceFileFile,
                        new File("./out/" + "Source" + sessionId + "Samo.class"),
                        runOutput.toString(),
                        runError.toString(),
                        inputs,
                        sessionId
                ));
            } catch (Exception e) {
                e.printStackTrace();
                if (!(e instanceof InterruptedException)) {
                    controller.sendRunResponse(new RunResponse("", "Could not run!", true, true), sessionId);
                    controller.sendProgramFinishedResponse(
                            new FinishedResponse(true, "<b style=\"color:red\">Program has exited.</b>"), sessionId
                    );
                }
            }
        }
    }
}