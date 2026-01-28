package com.example.audio;

import org.junit.Before;
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
 * PcmToMp3Converter 单元测试
 */
public class PcmToMp3ConverterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private byte[] testPcmData;

    @Before
    public void setUp() {
        // 生成测试PCM数据（440Hz正弦波，500毫秒）
        testPcmData = AudioUtils.generateSineWavePcm(440, 500, 16000, 0.5);
    }

    @Test
    public void testDefaultConverter() throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();

        assertEquals(16000, converter.getSampleRate());
        assertEquals(1, converter.getChannels());
        assertEquals(16, converter.getSampleSizeInBits());
        assertEquals(128, converter.getBitrate());
        assertEquals(5, converter.getQuality());
    }

    @Test
    public void testBuilderPattern() {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(44100)
                .channels(2)
                .sampleSizeInBits(16)
                .bitrate(256)
                .quality(2)
                .build();

        assertEquals(44100, converter.getSampleRate());
        assertEquals(2, converter.getChannels());
        assertEquals(16, converter.getSampleSizeInBits());
        assertEquals(256, converter.getBitrate());
        assertEquals(2, converter.getQuality());
    }

    @Test
    public void testConvertByteArray() throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();

        byte[] mp3Data = converter.convert(testPcmData);

        assertNotNull(mp3Data);
        assertTrue("MP3数据应该非空", mp3Data.length > 0);
        assertTrue("MP3应该比PCM小", mp3Data.length < testPcmData.length);

        // 验证MP3文件头（ID3标签或帧同步字）
        // MP3帧同步字节通常以0xFF开头
        boolean validMp3 = (mp3Data[0] == (byte) 0xFF) ||
                (mp3Data[0] == 'I' && mp3Data[1] == 'D' && mp3Data[2] == '3');
        assertTrue("应该是有效的MP3格式", validMp3);
    }

    @Test
    public void testConvertStream() throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(testPcmData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        converter.convert(inputStream, outputStream);

        byte[] mp3Data = outputStream.toByteArray();
        assertNotNull(mp3Data);
        assertTrue("MP3数据应该非空", mp3Data.length > 0);
    }

    @Test
    public void testConvertFile() throws IOException {
        // 创建临时PCM文件
        File pcmFile = tempFolder.newFile("test.pcm");
        try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
            fos.write(testPcmData);
        }

        File mp3File = new File(tempFolder.getRoot(), "test.mp3");

        PcmToMp3Converter converter = new PcmToMp3Converter();
        converter.convert(pcmFile, mp3File);

        assertTrue("MP3文件应该存在", mp3File.exists());
        assertTrue("MP3文件应该非空", mp3File.length() > 0);
        assertTrue("MP3应该比PCM小", mp3File.length() < pcmFile.length());
    }

    @Test
    public void testConvertFilePath() throws IOException {
        // 创建临时PCM文件
        File pcmFile = tempFolder.newFile("test2.pcm");
        try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
            fos.write(testPcmData);
        }

        String mp3FilePath = tempFolder.getRoot().getPath() + "/test2.mp3";

        PcmToMp3Converter converter = new PcmToMp3Converter();
        converter.convert(pcmFile.getPath(), mp3FilePath);

        File mp3File = new File(mp3FilePath);
        assertTrue("MP3文件应该存在", mp3File.exists());
        assertTrue("MP3文件应该非空", mp3File.length() > 0);
    }

    @Test
    public void testDifferentBitrates() throws IOException {
        // 测试不同比特率
        int[] bitrates = {64, 128, 192};
        int[] sizes = new int[bitrates.length];

        for (int i = 0; i < bitrates.length; i++) {
            PcmToMp3Converter converter = PcmToMp3Converter.builder()
                    .bitrate(bitrates[i])
                    .build();

            byte[] mp3Data = converter.convert(testPcmData);
            sizes[i] = mp3Data.length;
        }

        // 验证比特率越高，文件越大
        assertTrue("128kbps应该比64kbps大", sizes[1] > sizes[0]);
        assertTrue("192kbps应该比128kbps大", sizes[2] > sizes[1]);
    }

    @Test
    public void testStereoConversion() throws IOException {
        // 生成立体声PCM数据
        byte[] stereoPcm = AudioUtils.generateSineWavePcm(440, 500, 44100, 0.5);
        // 复制为双声道（简单地重复数据）
        byte[] stereoPcmData = new byte[stereoPcm.length * 2];
        for (int i = 0; i < stereoPcm.length / 2; i++) {
            // 左声道
            stereoPcmData[i * 4] = stereoPcm[i * 2];
            stereoPcmData[i * 4 + 1] = stereoPcm[i * 2 + 1];
            // 右声道
            stereoPcmData[i * 4 + 2] = stereoPcm[i * 2];
            stereoPcmData[i * 4 + 3] = stereoPcm[i * 2 + 1];
        }

        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(44100)
                .channels(2)
                .bitrate(192)
                .build();

        byte[] mp3Data = converter.convert(stereoPcmData);

        assertNotNull(mp3Data);
        assertTrue("MP3数据应该非空", mp3Data.length > 0);
    }
}
