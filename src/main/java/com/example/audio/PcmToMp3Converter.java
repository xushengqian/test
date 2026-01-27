package com.example.audio;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PcmToMp3Converter {
    
    // Path to the lame executable. Defaults to "lame" (in PATH).
    private final String lamePath;

    public PcmToMp3Converter() {
        this("lame");
    }

    public PcmToMp3Converter(String lamePath) {
        this.lamePath = lamePath;
    }

    /**
     * Converts PCM InputStream to MP3 OutputStream using LAME.
     * This method blocks until the conversion is complete.
     */
    public void convert(InputStream pcmIn, OutputStream mp3Out, AudioConfig config) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(lamePath);
        
        // Input options
        command.add("-r"); // Assume Raw PCM
        command.add("-s"); command.add(String.valueOf(config.getSampleRate() / 1000.0)); // Sample rate in kHz
        command.add("--bitwidth"); command.add(String.valueOf(config.getSampleSizeInBits()));
        
        if (!config.isSigned()) {
            command.add("--unsigned"); 
        }
        
        if (config.isBigEndian()) {
            command.add("-x"); // Swap bytes
        }

        if (config.getChannels() == 1) {
            command.add("-m"); command.add("m"); // Mono
        } else {
            command.add("-m"); command.add("s"); // Stereo (or j for joint)
        }

        // Standard input/output
        command.add("-");
        command.add("-");

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // Threads to handle streams...
        
        // Thread to write input (PCM data) to LAME's stdin
        Thread inputThread = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                IOUtils.copy(pcmIn, stdin);
            } catch (IOException e) {
                // If the pipe breaks (e.g. process finished early), we stop writing
            }
        });
        
        // Thread to consume stderr to prevent buffer filling and blocking
        Thread errorThread = new Thread(() -> {
            try (InputStream stderr = process.getErrorStream()) {
                byte[] buffer = new byte[1024];
                while (stderr.read(buffer) != -1) {}
            } catch (IOException e) {
                // Ignore errors reading stderr
            }
        });

        inputThread.start();
        errorThread.start();

        // Main thread reads LAME's stdout (MP3 data) and writes to mp3Out
        try {
            IOUtils.copy(process.getInputStream(), mp3Out);
        } catch (IOException e) {
            throw new IOException("Error copying MP3 output", e);
        }

        // Wait for process to finish
        int exitCode = process.waitFor();
        
        // Join threads to ensure cleanup
        try {
            inputThread.join(1000);
            errorThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (exitCode != 0) {
            throw new IOException("LAME process exited with code " + exitCode);
        }
    }
}
