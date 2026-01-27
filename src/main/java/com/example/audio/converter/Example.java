package com.example.audio.converter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PCM到MP3转换示例代码
 */
public class Example {

    public static void main(String[] args) throws Exception {
        System.out.println("=== PCM到MP3转换示例 ===\n");

        // 生成测试用的PCM数据
        byte[] pcmData = generateTestPcmData();
        System.out.println("生成测试PCM数据: " + pcmData.length + " bytes");

        // 示例1：基本转换
        example1_BasicConversion(pcmData);

        // 示例2：流式转换
        example2_StreamConversion(pcmData);

        // 示例3：增量编码
        example3_IncrementalEncoding(pcmData);

        // 示例4：使用输出流
        example4_OutputStreamUsage(pcmData);

        // 示例5：实时流转换
        example5_RealtimeConversion(pcmData);
    }

    /**
     * 示例1：基本的字节数组转换
     */
    static void example1_BasicConversion(byte[] pcmData) throws IOException {
        System.out.println("\n--- 示例1: 基本字节数组转换 ---");

        // 创建配置：16kHz单声道16位PCM，128kbps MP3
        AudioConfig config = AudioConfig.voiceQuality();

        // 创建转换器并转换
        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        byte[] mp3Data = converter.convert(pcmData);

        System.out.println("转换完成: PCM " + pcmData.length + " bytes -> MP3 " + mp3Data.length + " bytes");
        System.out.println("压缩比: " + String.format("%.2f%%", (double) mp3Data.length / pcmData.length * 100));
    }

    /**
     * 示例2：输入输出流转换
     */
    static void example2_StreamConversion(byte[] pcmData) throws IOException {
        System.out.println("\n--- 示例2: 输入输出流转换 ---");

        AudioConfig config = AudioConfig.voiceQuality();
        PcmToMp3Converter converter = new PcmToMp3Converter(config);

        try (InputStream pcmInput = new ByteArrayInputStream(pcmData);
             ByteArrayOutputStream mp3Output = new ByteArrayOutputStream()) {

            converter.convert(pcmInput, mp3Output);

            byte[] mp3Data = mp3Output.toByteArray();
            System.out.println("流转换完成: " + mp3Data.length + " bytes");
        }
    }

    /**
     * 示例3：增量编码（适用于实时场景）
     */
    static void example3_IncrementalEncoding(byte[] pcmData) throws IOException {
        System.out.println("\n--- 示例3: 增量编码 ---");

        AudioConfig config = AudioConfig.voiceQuality();
        PcmToMp3Converter converter = new PcmToMp3Converter(config);

        // 创建流式编码器
        PcmToMp3Converter.StreamEncoder encoder = converter.createStreamEncoder();

        ByteArrayOutputStream mp3Output = new ByteArrayOutputStream();
        int chunkSize = 1600; // 每次处理100ms的数据（16000Hz * 2bytes * 0.1s）
        int chunkCount = 0;

        // 模拟分块接收PCM数据
        int offset = 0;
        while (offset < pcmData.length) {
            int len = Math.min(chunkSize, pcmData.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcmData, offset, chunk, 0, len);

            // 编码当前块
            byte[] mp3Chunk = encoder.encode(chunk);
            if (mp3Chunk.length > 0) {
                mp3Output.write(mp3Chunk);
                chunkCount++;
            }

            offset += len;
        }

        // 刷新剩余数据
        byte[] remaining = encoder.flush();
        if (remaining.length > 0) {
            mp3Output.write(remaining);
        }

        encoder.close();

        System.out.println("增量编码完成，输出 " + chunkCount + " 个MP3块");
        System.out.println("总输出: " + mp3Output.size() + " bytes");
    }

    /**
     * 示例4：使用MP3编码输出流
     */
    static void example4_OutputStreamUsage(byte[] pcmData) throws IOException {
        System.out.println("\n--- 示例4: 使用MP3编码输出流 ---");

        AudioConfig config = AudioConfig.voiceQuality();
        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();

        try (Mp3EncoderOutputStream mp3Out = new Mp3EncoderOutputStream(rawOutput, config)) {
            // 直接写入PCM数据，会自动编码为MP3
            int chunkSize = 800;
            int offset = 0;

            while (offset < pcmData.length) {
                int len = Math.min(chunkSize, pcmData.length - offset);
                mp3Out.write(pcmData, offset, len);
                offset += len;
            }

            System.out.println("写入完成，PCM: " + mp3Out.getTotalBytesWritten() + " bytes");
        }

        System.out.println("MP3输出: " + rawOutput.size() + " bytes");
    }

    /**
     * 示例5：实时流转换
     */
    static void example5_RealtimeConversion(byte[] pcmData) throws Exception {
        System.out.println("\n--- 示例5: 实时流转换 ---");

        AudioConfig config = AudioConfig.voiceQuality();
        List<byte[]> mp3Chunks = new ArrayList<>();

        // 创建实时转换器，设置MP3数据回调
        RealtimePcmToMp3Converter converter = new RealtimePcmToMp3Converter(config, mp3Chunk -> {
            synchronized (mp3Chunks) {
                mp3Chunks.add(mp3Chunk);
                // 在实际应用中，这里可以将数据发送给客户端
                // webSocket.sendBinary(mp3Chunk);
            }
        });

        // 启动转换器
        converter.start();

        // 模拟实时输入PCM数据
        int chunkSize = 1600;
        int offset = 0;

        while (offset < pcmData.length) {
            int len = Math.min(chunkSize, pcmData.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcmData, offset, chunk, 0, len);

            converter.feed(chunk);
            offset += len;

            // 模拟实时延迟
            Thread.sleep(10);
        }

        // 等待处理完成
        Thread.sleep(300);

        // 停止转换器
        converter.stop();

        int totalMp3Bytes = mp3Chunks.stream().mapToInt(c -> c.length).sum();
        System.out.println("实时转换完成，收到 " + mp3Chunks.size() + " 个MP3块");
        System.out.println("总输出: " + totalMp3Bytes + " bytes");
    }

    /**
     * 生成测试用的PCM数据（3秒440Hz正弦波）
     */
    static byte[] generateTestPcmData() {
        int sampleRate = 16000;
        int channels = 1;
        int bitDepth = 16;
        int durationMs = 3000;
        double frequency = 440.0;

        int bytesPerSample = (bitDepth / 8) * channels;
        int totalSamples = sampleRate * durationMs / 1000;
        byte[] pcmData = new byte[totalSamples * bytesPerSample];

        for (int i = 0; i < totalSamples; i++) {
            double time = (double) i / sampleRate;
            double amplitude = Math.sin(2 * Math.PI * frequency * time);
            short sample = (short) (amplitude * Short.MAX_VALUE * 0.8);

            int offset = i * bytesPerSample;
            pcmData[offset] = (byte) (sample & 0xFF);
            pcmData[offset + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return pcmData;
    }
}
