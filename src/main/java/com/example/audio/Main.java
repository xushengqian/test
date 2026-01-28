package com.example.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            String pcmPath = "test.pcm";
            String mp3Path = "test.mp3";
            
            // 1. 生成测试 PCM 文件 (44100Hz, 16bit, Mono, 5秒)
            System.out.println("Generating dummy PCM file...");
            generatePcmFile(pcmPath, 44100, 5);
            
            // 2. 转换 PCM 到 MP3
            File pcmFile = new File(pcmPath);
            File mp3File = new File(mp3Path);
            
            System.out.println("Converting PCM to MP3...");
            long startTime = System.currentTimeMillis();
            
            // 假设 PCM 是 44100Hz, 单声道, 128kbps
            PcmToMp3Converter.convertPcmToMp3(pcmFile, mp3File, 44100, 1, 128);
            
            long endTime = System.currentTimeMillis();
            System.out.println("Conversion finished in " + (endTime - startTime) + "ms");
            System.out.println("Output file: " + mp3File.getAbsolutePath());
            System.out.println("File size: " + mp3File.length() + " bytes");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generatePcmFile(String filePath, int sampleRate, int durationSeconds) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            double frequency = 440.0; // 440Hz A4 note
            int numSamples = sampleRate * durationSeconds;
            
            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                short sample = (short) (Math.sin(angle) * 32767);
                // Little-endian write
                fos.write(sample & 0xFF);
                fos.write((sample >> 8) & 0xFF);
            }
        }
    }
}
