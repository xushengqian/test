package com.example.audio;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * AudioUtils 单元测试
 */
public class AudioUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGenerateSineWavePcm() {
        double frequency = 440;
        int durationMs = 1000;
        int sampleRate = 16000;
        double amplitude = 0.5;

        byte[] pcmData = AudioUtils.generateSineWavePcm(frequency, durationMs, sampleRate, amplitude);

        // 预期大小：采样率 * 时长(秒) * 2字节(16位)
        int expectedSize = sampleRate * durationMs / 1000 * 2;
        assertEquals("PCM数据大小应该正确", expectedSize, pcmData.length);
    }

    @Test
    public void testGenerateTestPcm() {
        byte[] pcmData = AudioUtils.generateTestPcm();

        assertNotNull(pcmData);
        // 16000 * 1秒 * 2字节 = 32000字节
        assertEquals("测试PCM大小应该是32000字节", 32000, pcmData.length);
    }

    @Test
    public void testPcmToMp3ByteArray() throws IOException {
        byte[] pcmData = AudioUtils.generateTestPcm();

        byte[] mp3Data = AudioUtils.pcmToMp3(pcmData);

        assertNotNull(mp3Data);
        assertTrue("MP3应该比PCM小", mp3Data.length < pcmData.length);
    }

    @Test
    public void testPcmToMp3WithParams() throws IOException {
        byte[] pcmData = AudioUtils.generateSineWavePcm(440, 500, 8000, 0.5);

        byte[] mp3Data = AudioUtils.pcmToMp3(pcmData, 8000, 1);

        assertNotNull(mp3Data);
        assertTrue("MP3数据应该非空", mp3Data.length > 0);
    }

    @Test
    public void testPcmToMp3WithBitrate() throws IOException {
        byte[] pcmData = AudioUtils.generateTestPcm();

        byte[] mp3Data64 = AudioUtils.pcmToMp3(pcmData, 16000, 1, 64);
        byte[] mp3Data128 = AudioUtils.pcmToMp3(pcmData, 16000, 1, 128);

        assertNotNull(mp3Data64);
        assertNotNull(mp3Data128);
        assertTrue("128kbps应该比64kbps大", mp3Data128.length > mp3Data64.length);
    }

    @Test
    public void testPcmToMp3FilePath() throws IOException {
        byte[] pcmData = AudioUtils.generateTestPcm();

        File pcmFile = tempFolder.newFile("test.pcm");
        try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
            fos.write(pcmData);
        }

        String mp3Path = tempFolder.getRoot().getPath() + "/test.mp3";
        AudioUtils.pcmToMp3(pcmFile.getPath(), mp3Path);

        File mp3File = new File(mp3Path);
        assertTrue("MP3文件应该存在", mp3File.exists());
        assertTrue("MP3文件应该非空", mp3File.length() > 0);
    }

    @Test
    public void testPcmToMp3Stream() throws IOException {
        byte[] pcmData = AudioUtils.generateTestPcm();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(pcmData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        AudioUtils.pcmToMp3(inputStream, outputStream, 16000, 1);

        byte[] mp3Data = outputStream.toByteArray();
        assertNotNull(mp3Data);
        assertTrue("MP3数据应该非空", mp3Data.length > 0);
    }

    @Test
    public void testCalculateDurationMs() {
        // 16000采样率, 单声道, 16位, 32000字节 = 1秒
        long duration = AudioUtils.calculateDurationMs(32000, 16000, 1, 16);
        assertEquals("时长应该是1000毫秒", 1000, duration);

        // 44100采样率, 立体声, 16位, 176400字节 = 1秒
        duration = AudioUtils.calculateDurationMs(176400, 44100, 2, 16);
        assertEquals("时长应该是1000毫秒", 1000, duration);
    }

    @Test
    public void testCalculatePcmSize() {
        // 1秒, 16000采样率, 单声道, 16位 = 32000字节
        long size = AudioUtils.calculatePcmSize(1000, 16000, 1, 16);
        assertEquals("大小应该是32000字节", 32000, size);

        // 1秒, 44100采样率, 立体声, 16位 = 176400字节
        size = AudioUtils.calculatePcmSize(1000, 44100, 2, 16);
        assertEquals("大小应该是176400字节", 176400, size);
    }

    @Test
    public void testBatchConvert() throws IOException {
        // 创建输入目录和几个PCM文件
        File inputDir = tempFolder.newFolder("input");
        File outputDir = new File(tempFolder.getRoot(), "output");

        byte[] pcmData = AudioUtils.generateTestPcm();

        for (int i = 1; i <= 3; i++) {
            File pcmFile = new File(inputDir, "audio" + i + ".pcm");
            try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
                fos.write(pcmData);
            }
        }

        // 批量转换
        AudioUtils.batchConvert(inputDir.getPath(), outputDir.getPath(), 16000, 1);

        // 验证输出
        assertTrue("输出目录应该存在", outputDir.exists());
        File[] mp3Files = outputDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        assertNotNull(mp3Files);
        assertEquals("应该有3个MP3文件", 3, mp3Files.length);
    }

    @Test
    public void testDifferentSampleRates() throws IOException {
        int[] sampleRates = {8000, 16000, 22050, 44100};

        for (int sampleRate : sampleRates) {
            byte[] pcmData = AudioUtils.generateSineWavePcm(440, 500, sampleRate, 0.5);
            byte[] mp3Data = AudioUtils.pcmToMp3(pcmData, sampleRate, 1);

            assertNotNull("采样率" + sampleRate + "转换应该成功", mp3Data);
            assertTrue("采样率" + sampleRate + "MP3应该非空", mp3Data.length > 0);
        }
    }
}
