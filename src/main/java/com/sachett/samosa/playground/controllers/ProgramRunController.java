package com.sachett.samosa.playground.controllers;

import com.sachett.samosa.playground.models.FinishedResponse;
import com.sachett.samosa.playground.models.RunRequest;
import com.sachett.samosa.playground.models.RunResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Controller
public class ProgramRunController {

    private static final String compilerExecutablePath = "./src/main/resources/binaries/samosac-1.0-SNAPSHOT-full.jar";
    private static final BufferedReader systemInBufferedReader = new BufferedReader(new InputStreamReader(System.in));

    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;

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
            ProgramRunner programRunner = new ProgramRunner(this, sessionId, message.getProgram());
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

        public ProgramRunner(ProgramRunController controller, String sessionId, String program) {
            this.controller = controller;
            this.sessionId = sessionId;
            this.theProgram = program;

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
                File file = new File(sourceFile);
                file.createNewFile();

                FileWriter sourceFileWriter = new FileWriter(file);
                sourceFileWriter.write(theProgram);
                sourceFileWriter.close();

                Process compileProcess = Runtime.getRuntime().exec(new String[]{"java", "-jar", compilerExecutablePath, sourceFile});
                InputStream compilationResultStream = compileProcess.getInputStream();
                OutputStream compilationOutputStream = compileProcess.getOutputStream();
                InputStream compilationErrorStream = compileProcess.getErrorStream();

                boolean compileTimeout = !compileProcess.waitFor(15, TimeUnit.SECONDS);

                byte[] bCompilationResult = new byte[compilationResultStream.available()];
                byte[] bCompilationError = new byte[compilationErrorStream.available()];
                int readBytes = compilationResultStream.read(bCompilationResult, 0, bCompilationResult.length);
                readBytes = compilationErrorStream.read(bCompilationError, 0, bCompilationError.length);

                String compilationResult = new String(bCompilationResult);
                String compilationError = new String(bCompilationError);

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

                StringBuilder runOutput = new StringBuilder();
                StringBuilder runError = new StringBuilder();

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