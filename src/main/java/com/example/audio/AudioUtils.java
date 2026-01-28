package com.example.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 音频工具类
 * <p>
 * 提供PCM和MP3音频处理的静态方法
 * </p>
 *
 * @author example
 * @version 1.0.0
 */
public class AudioUtils {

    private static final Logger logger = LoggerFactory.getLogger(AudioUtils.class);

    private AudioUtils() {
        // 工具类禁止实例化
    }

    /**
     * 使用默认参数将PCM文件转换为MP3文件
     * 默认参数：16000Hz, 单声道, 16位, 128kbps
     *
     * @param pcmFilePath PCM输入文件路径
     * @param mp3FilePath MP3输出文件路径
     * @throws IOException 如果转换失败
     */
    public static void pcmToMp3(String pcmFilePath, String mp3FilePath) throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();
        converter.convert(pcmFilePath, mp3FilePath);
    }

    /**
     * 使用指定参数将PCM文件转换为MP3文件
     *
     * @param pcmFilePath PCM输入文件路径
     * @param mp3FilePath MP3输出文件路径
     * @param sampleRate  采样率 (Hz)
     * @param channels    声道数
     * @throws IOException 如果转换失败
     */
    public static void pcmToMp3(String pcmFilePath, String mp3FilePath,
                                int sampleRate, int channels) throws IOException {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .build();
        converter.convert(pcmFilePath, mp3FilePath);
    }

    /**
     * 使用指定参数将PCM文件转换为MP3文件
     *
     * @param pcmFilePath PCM输入文件路径
     * @param mp3FilePath MP3输出文件路径
     * @param sampleRate  采样率 (Hz)
     * @param channels    声道数
     * @param bitrate     MP3比特率 (kbps)
     * @throws IOException 如果转换失败
     */
    public static void pcmToMp3(String pcmFilePath, String mp3FilePath,
                                int sampleRate, int channels, int bitrate) throws IOException {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .bitrate(bitrate)
                .build();
        converter.convert(pcmFilePath, mp3FilePath);
    }

    /**
     * 使用默认参数将PCM字节数组转换为MP3字节数组
     *
     * @param pcmData PCM音频数据
     * @return MP3音频数据
     * @throws IOException 如果转换失败
     */
    public static byte[] pcmToMp3(byte[] pcmData) throws IOException {
        PcmToMp3Converter converter = new PcmToMp3Converter();
        return converter.convert(pcmData);
    }

    /**
     * 使用指定参数将PCM字节数组转换为MP3字节数组
     *
     * @param pcmData    PCM音频数据
     * @param sampleRate 采样率 (Hz)
     * @param channels   声道数
     * @return MP3音频数据
     * @throws IOException 如果转换失败
     */
    public static byte[] pcmToMp3(byte[] pcmData, int sampleRate, int channels) throws IOException {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .build();
        return converter.convert(pcmData);
    }

    /**
     * 使用指定参数将PCM字节数组转换为MP3字节数组
     *
     * @param pcmData    PCM音频数据
     * @param sampleRate 采样率 (Hz)
     * @param channels   声道数
     * @param bitrate    MP3比特率 (kbps)
     * @return MP3音频数据
     * @throws IOException 如果转换失败
     */
    public static byte[] pcmToMp3(byte[] pcmData, int sampleRate, int channels, int bitrate) throws IOException {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .bitrate(bitrate)
                .build();
        return converter.convert(pcmData);
    }

    /**
     * 将PCM输入流转换为MP3并写入输出流
     *
     * @param pcmInputStream  PCM输入流
     * @param mp3OutputStream MP3输出流
     * @param sampleRate      采样率 (Hz)
     * @param channels        声道数
     * @throws IOException 如果转换失败
     */
    public static void pcmToMp3(InputStream pcmInputStream, OutputStream mp3OutputStream,
                                int sampleRate, int channels) throws IOException {
        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .build();
        converter.convert(pcmInputStream, mp3OutputStream);
    }

    /**
     * 生成测试用的PCM数据（正弦波）
     *
     * @param frequency    频率 (Hz)
     * @param durationMs   时长 (毫秒)
     * @param sampleRate   采样率 (Hz)
     * @param amplitude    振幅 (0.0 - 1.0)
     * @return PCM字节数组（16位有符号小端格式）
     */
    public static byte[] generateSineWavePcm(double frequency, int durationMs,
                                              int sampleRate, double amplitude) {
        int numSamples = (int) (sampleRate * durationMs / 1000.0);
        byte[] pcmData = new byte[numSamples * 2]; // 16位 = 2字节

        ByteBuffer buffer = ByteBuffer.wrap(pcmData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double sampleValue = amplitude * Math.sin(2 * Math.PI * frequency * time);
            short sample = (short) (sampleValue * Short.MAX_VALUE);
            buffer.putShort(sample);
        }

        return pcmData;
    }

    /**
     * 生成测试用的PCM数据（440Hz A调，1秒）
     *
     * @return PCM字节数组
     */
    public static byte[] generateTestPcm() {
        return generateSineWavePcm(440, 1000, 16000, 0.5);
    }

    /**
     * 批量将目录下的PCM文件转换为MP3
     *
     * @param inputDir   输入目录
     * @param outputDir  输出目录
     * @param sampleRate 采样率 (Hz)
     * @param channels   声道数
     * @throws IOException 如果转换失败
     */
    public static void batchConvert(String inputDir, String outputDir,
                                    int sampleRate, int channels) throws IOException {
        Path inputPath = Paths.get(inputDir);
        Path outputPath = Paths.get(outputDir);

        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        PcmToMp3Converter converter = PcmToMp3Converter.builder()
                .sampleRate(sampleRate)
                .channels(channels)
                .build();

        Files.list(inputPath)
                .filter(p -> p.toString().toLowerCase().endsWith(".pcm"))
                .forEach(pcmFile -> {
                    String fileName = pcmFile.getFileName().toString();
                    String mp3FileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".mp3";
                    Path mp3File = outputPath.resolve(mp3FileName);

                    try {
                        converter.convert(pcmFile.toFile(), mp3File.toFile());
                        logger.info("已转换: {} -> {}", fileName, mp3FileName);
                    } catch (IOException e) {
                        logger.error("转换失败: {}", fileName, e);
                    }
                });
    }

    /**
     * 计算PCM数据的时长（毫秒）
     *
     * @param pcmDataLength    PCM数据长度（字节）
     * @param sampleRate       采样率 (Hz)
     * @param channels         声道数
     * @param sampleSizeInBits 位深度
     * @return 时长（毫秒）
     */
    public static long calculateDurationMs(long pcmDataLength, int sampleRate,
                                           int channels, int sampleSizeInBits) {
        int bytesPerSample = sampleSizeInBits / 8;
        long numSamples = pcmDataLength / (channels * bytesPerSample);
        return (numSamples * 1000) / sampleRate;
    }

    /**
     * 计算给定时长的PCM数据大小（字节）
     *
     * @param durationMs       时长（毫秒）
     * @param sampleRate       采样率 (Hz)
     * @param channels         声道数
     * @param sampleSizeInBits 位深度
     * @return 数据大小（字节）
     */
    public static long calculatePcmSize(long durationMs, int sampleRate,
                                        int channels, int sampleSizeInBits) {
        int bytesPerSample = sampleSizeInBits / 8;
        long numSamples = (durationMs * sampleRate) / 1000;
        return numSamples * channels * bytesPerSample;
    }
}
