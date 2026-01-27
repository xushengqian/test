package com.example.audio.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PCM到MP3转换器测试类
 */
class PcmToMp3ConverterTest {

    @TempDir
    Path tempDir;

    /**
     * 生成测试用的PCM数据（正弦波）
     *
     * @param sampleRate 采样率
     * @param channels   声道数
     * @param bitDepth   位深度
     * @param durationMs 持续时间（毫秒）
     * @param frequency  频率（Hz）
     * @return PCM字节数组
     */
    private byte[] generateSineWave(int sampleRate, int channels, int bitDepth,
                                    int durationMs, double frequency) {
        int bytesPerSample = (bitDepth / 8) * channels;
        int totalSamples = sampleRate * durationMs / 1000;
        byte[] pcmData = new byte[totalSamples * bytesPerSample];

        for (int i = 0; i < totalSamples; i++) {
            double time = (double) i / sampleRate;
            double amplitude = Math.sin(2 * Math.PI * frequency * time);
            short sample = (short) (amplitude * Short.MAX_VALUE * 0.8);

            for (int ch = 0; ch < channels; ch++) {
                int offset = i * bytesPerSample + ch * 2;
                // Little-endian
                pcmData[offset] = (byte) (sample & 0xFF);
                pcmData[offset + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        return pcmData;
    }

    @Test
    void testBasicConversion() throws IOException {
        // 准备测试数据：1秒的440Hz正弦波
        AudioConfig config = AudioConfig.voiceQuality();
        byte[] pcmData = generateSineWave(16000, 1, 16, 1000, 440);

        // 执行转换
        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        byte[] mp3Data = converter.convert(pcmData);

        // 验证
        assertNotNull(mp3Data);
        assertTrue(mp3Data.length > 0, "MP3数据应该有内容");
        assertTrue(mp3Data.length < pcmData.length, "MP3数据应该比PCM数据小");

        // 验证MP3帧头（应该以0xFF 0xFB开头，表示MPEG Audio Layer III）
        assertTrue((mp3Data[0] & 0xFF) == 0xFF, "应该以MP3帧同步字节开头");
    }

    @Test
    void testStreamConversion() throws IOException {
        AudioConfig config = new AudioConfig(8000, 1, 16, 64, 5);
        byte[] pcmData = generateSineWave(8000, 1, 16, 2000, 300);

        ByteArrayInputStream pcmInput = new ByteArrayInputStream(pcmData);
        ByteArrayOutputStream mp3Output = new ByteArrayOutputStream();

        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        converter.convert(pcmInput, mp3Output);

        byte[] mp3Data = mp3Output.toByteArray();

        assertNotNull(mp3Data);
        assertTrue(mp3Data.length > 0);
        System.out.println("PCM size: " + pcmData.length + " bytes, MP3 size: " + mp3Data.length + " bytes");
    }

    @Test
    void testFileConversion() throws IOException {
        AudioConfig config = AudioConfig.voiceQuality();
        byte[] pcmData = generateSineWave(16000, 1, 16, 3000, 440);

        // 创建临时PCM文件
        File pcmFile = tempDir.resolve("test.pcm").toFile();
        File mp3File = tempDir.resolve("test.mp3").toFile();

        try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
            fos.write(pcmData);
        }

        // 执行转换
        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        converter.convertFile(pcmFile, mp3File);

        // 验证
        assertTrue(mp3File.exists());
        assertTrue(mp3File.length() > 0);
        System.out.println("PCM file size: " + pcmFile.length() + " bytes");
        System.out.println("MP3 file size: " + mp3File.length() + " bytes");
    }

    @Test
    void testStreamEncoder() throws IOException {
        AudioConfig config = AudioConfig.voiceQuality();
        byte[] pcmData = generateSineWave(16000, 1, 16, 2000, 440);
        int chunkSize = 1600; // 100ms of audio

        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        PcmToMp3Converter.StreamEncoder encoder = converter.createStreamEncoder();

        ByteArrayOutputStream mp3Output = new ByteArrayOutputStream();

        // 分块编码
        int offset = 0;
        while (offset < pcmData.length) {
            int len = Math.min(chunkSize, pcmData.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcmData, offset, chunk, 0, len);

            byte[] mp3Chunk = encoder.encode(chunk);
            if (mp3Chunk.length > 0) {
                mp3Output.write(mp3Chunk);
            }
            offset += len;
        }

        // 刷新并关闭
        byte[] remaining = encoder.flush();
        if (remaining.length > 0) {
            mp3Output.write(remaining);
        }
        encoder.close();

        byte[] mp3Data = mp3Output.toByteArray();
        assertTrue(mp3Data.length > 0);
        System.out.println("Stream encoding - PCM: " + pcmData.length + " bytes -> MP3: " + mp3Data.length + " bytes");
    }

    @Test
    void testMp3EncoderOutputStream() throws IOException {
        AudioConfig config = AudioConfig.voiceQuality();
        byte[] pcmData = generateSineWave(16000, 1, 16, 1500, 440);

        ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();

        try (Mp3EncoderOutputStream mp3Output = new Mp3EncoderOutputStream(rawOutput, config)) {
            // 模拟流式写入
            int offset = 0;
            int chunkSize = 800;
            while (offset < pcmData.length) {
                int len = Math.min(chunkSize, pcmData.length - offset);
                mp3Output.write(pcmData, offset, len);
                offset += len;
            }
        }

        byte[] mp3Data = rawOutput.toByteArray();
        assertTrue(mp3Data.length > 0);
        System.out.println("OutputStream encoding - PCM: " + pcmData.length + " bytes -> MP3: " + mp3Data.length + " bytes");
    }

    @Test
    void testStereoConversion() throws IOException {
        // 测试立体声转换
        AudioConfig config = new AudioConfig(44100, 2, 16, 192, 5);
        byte[] pcmData = generateSineWave(44100, 2, 16, 1000, 440);

        PcmToMp3Converter converter = new PcmToMp3Converter(config);
        byte[] mp3Data = converter.convert(pcmData);

        assertNotNull(mp3Data);
        assertTrue(mp3Data.length > 0);
        System.out.println("Stereo conversion - PCM: " + pcmData.length + " bytes -> MP3: " + mp3Data.length + " bytes");
    }

    @Test
    void testEmptyInput() throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();

        byte[] result1 = converter.convert(new byte[0]);
        assertEquals(0, result1.length);

        byte[] result2 = converter.convert(null);
        assertEquals(0, result2.length);
    }

    @Test
    void testRealtimeConverter() throws Exception {
        AudioConfig config = AudioConfig.voiceQuality();
        byte[] pcmData = generateSineWave(16000, 1, 16, 2000, 440);
        int chunkSize = 1600;

        List<byte[]> mp3Chunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        RealtimePcmToMp3Converter converter = new RealtimePcmToMp3Converter(config, chunk -> {
            synchronized (mp3Chunks) {
                mp3Chunks.add(chunk);
            }
        });

        converter.start();
        assertTrue(converter.isRunning());

        // 模拟实时输入
        int offset = 0;
        while (offset < pcmData.length) {
            int len = Math.min(chunkSize, pcmData.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(pcmData, offset, chunk, 0, len);

            assertTrue(converter.feed(chunk));
            offset += len;

            // 模拟实时延迟
            Thread.sleep(10);
        }

        // 等待处理完成
        Thread.sleep(500);
        converter.stop();

        // 验证
        assertFalse(converter.isRunning());
        assertTrue(mp3Chunks.size() > 0, "应该收到MP3数据块");

        int totalMp3Bytes = mp3Chunks.stream().mapToInt(c -> c.length).sum();
        assertTrue(totalMp3Bytes > 0);
        System.out.println("Realtime conversion - PCM: " + pcmData.length + " bytes -> MP3: " + totalMp3Bytes + " bytes");
        System.out.println("Received " + mp3Chunks.size() + " MP3 chunks");
    }

    @Test
    void testAudioConfigPresets() {
        // 测试预设配置
        AudioConfig telephone = AudioConfig.telephoneQuality();
        assertEquals(8000, telephone.getSampleRate());
        assertEquals(1, telephone.getChannels());
        assertEquals(64, telephone.getBitRate());

        AudioConfig voice = AudioConfig.voiceQuality();
        assertEquals(16000, voice.getSampleRate());
        assertEquals(1, voice.getChannels());
        assertEquals(128, voice.getBitRate());

        AudioConfig cd = AudioConfig.cdQuality();
        assertEquals(44100, cd.getSampleRate());
        assertEquals(2, cd.getChannels());
        assertEquals(320, cd.getBitRate());

        AudioConfig pro = AudioConfig.professionalQuality();
        assertEquals(48000, pro.getSampleRate());
        assertEquals(2, pro.getChannels());
        assertEquals(320, pro.getBitRate());
    }

    @Test
    void testInvalidConfig() {
        // 测试无效配置应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            new AudioConfig().setSampleRate(-1).validate();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioConfig().setChannels(3).validate();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioConfig().setBitDepth(24).validate();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioConfig().setQuality(10).validate();
        });
    }
}
