package com.example.audio;

import de.sciss.jump3r.lowlevel.LameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.*;

/**
 * PCM转MP3转换器
 * <p>
 * 支持将PCM原始音频数据转换为MP3格式
 * 使用Jump3r（纯Java的LAME移植版本）进行编码
 * </p>
 *
 * @author example
 * @version 1.0.0
 */
public class PcmToMp3Converter {

    private static final Logger logger = LoggerFactory.getLogger(PcmToMp3Converter.class);

    /**
     * 默认采样率 (Hz)
     */
    public static final int DEFAULT_SAMPLE_RATE = 16000;

    /**
     * 默认声道数（单声道）
     */
    public static final int DEFAULT_CHANNELS = 1;

    /**
     * 默认位深度（16位）
     */
    public static final int DEFAULT_SAMPLE_SIZE_BITS = 16;

    /**
     * 默认MP3比特率 (kbps)
     */
    public static final int DEFAULT_BITRATE = 128;

    /**
     * 默认MP3质量 (0-9, 0最好，9最差)
     */
    public static final int DEFAULT_QUALITY = 5;

    private final int sampleRate;
    private final int channels;
    private final int sampleSizeInBits;
    private final int bitrate;
    private final int quality;

    /**
     * 使用默认参数创建转换器
     * 默认：16000Hz, 单声道, 16位, 128kbps
     */
    public PcmToMp3Converter() {
        this(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_SAMPLE_SIZE_BITS,
                DEFAULT_BITRATE, DEFAULT_QUALITY);
    }

    /**
     * 使用指定参数创建转换器
     *
     * @param sampleRate       采样率 (Hz)，常见值：8000, 16000, 22050, 44100, 48000
     * @param channels         声道数，1为单声道，2为立体声
     * @param sampleSizeInBits 位深度，通常为8或16
     * @param bitrate          MP3比特率 (kbps)，常见值：64, 128, 192, 256, 320
     * @param quality          编码质量 (0-9)，0最好，9最快
     */
    public PcmToMp3Converter(int sampleRate, int channels, int sampleSizeInBits,
                             int bitrate, int quality) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.sampleSizeInBits = sampleSizeInBits;
        this.bitrate = bitrate;
        this.quality = quality;
    }

    /**
     * 将PCM文件转换为MP3文件
     *
     * @param pcmFile PCM输入文件
     * @param mp3File MP3输出文件
     * @throws IOException 如果读写文件时发生错误
     */
    public void convert(File pcmFile, File mp3File) throws IOException {
        logger.info("开始转换: {} -> {}", pcmFile.getName(), mp3File.getName());

        try (FileInputStream fis = new FileInputStream(pcmFile);
             FileOutputStream fos = new FileOutputStream(mp3File)) {
            convert(fis, fos);
        }

        logger.info("转换完成: {}", mp3File.getName());
    }

    /**
     * 将PCM文件路径转换为MP3文件
     *
     * @param pcmFilePath PCM输入文件路径
     * @param mp3FilePath MP3输出文件路径
     * @throws IOException 如果读写文件时发生错误
     */
    public void convert(String pcmFilePath, String mp3FilePath) throws IOException {
        convert(new File(pcmFilePath), new File(mp3FilePath));
    }

    /**
     * 将PCM输入流转换为MP3并写入输出流
     *
     * @param pcmInputStream  PCM输入流
     * @param mp3OutputStream MP3输出流
     * @throws IOException 如果读写时发生错误
     */
    public void convert(InputStream pcmInputStream, OutputStream mp3OutputStream) throws IOException {
        // 创建音频格式
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                sampleSizeInBits,
                channels,
                channels * (sampleSizeInBits / 8), // frameSize
                sampleRate,
                false // little endian
        );

        // 创建LAME编码器
        // LameEncoder参数: audioFormat, bitrate, mpegMode(STEREO/MONO/etc), quality(0-9, 0最好)
        LameEncoder encoder = new LameEncoder(audioFormat, bitrate, 
                channels == 1 ? LameEncoder.CHANNEL_MODE_MONO : LameEncoder.CHANNEL_MODE_STEREO, 
                quality, false);

        byte[] pcmBuffer = new byte[encoder.getPCMBufferSize()];
        byte[] mp3Buffer = new byte[encoder.getPCMBufferSize()];

        int bytesRead;
        int bytesWritten;

        logger.debug("PCM格式: 采样率={}Hz, 声道={}, 位深={}bit",
                sampleRate, channels, sampleSizeInBits);
        logger.debug("MP3格式: 比特率={}kbps, 质量={}", bitrate, quality);

        // 读取PCM数据并编码为MP3
        while ((bytesRead = pcmInputStream.read(pcmBuffer)) > 0) {
            bytesWritten = encoder.encodeBuffer(pcmBuffer, 0, bytesRead, mp3Buffer);
            if (bytesWritten > 0) {
                mp3OutputStream.write(mp3Buffer, 0, bytesWritten);
            }
        }

        // 刷新编码器缓冲区
        bytesWritten = encoder.encodeFinish(mp3Buffer);
        if (bytesWritten > 0) {
            mp3OutputStream.write(mp3Buffer, 0, bytesWritten);
        }

        encoder.close();
        mp3OutputStream.flush();
    }

    /**
     * 将PCM字节数组转换为MP3字节数组
     *
     * @param pcmData PCM音频数据
     * @return MP3音频数据
     * @throws IOException 如果转换时发生错误
     */
    public byte[] convert(byte[] pcmData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        convert(bais, baos);

        return baos.toByteArray();
    }

    /**
     * 创建Builder用于配置转换器参数
     *
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 转换器构建器
     */
    public static class Builder {
        private int sampleRate = DEFAULT_SAMPLE_RATE;
        private int channels = DEFAULT_CHANNELS;
        private int sampleSizeInBits = DEFAULT_SAMPLE_SIZE_BITS;
        private int bitrate = DEFAULT_BITRATE;
        private int quality = DEFAULT_QUALITY;

        /**
         * 设置采样率
         *
         * @param sampleRate 采样率 (Hz)
         * @return Builder
         */
        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        /**
         * 设置声道数
         *
         * @param channels 声道数（1=单声道，2=立体声）
         * @return Builder
         */
        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        /**
         * 设置位深度
         *
         * @param sampleSizeInBits 位深度（通常为8或16）
         * @return Builder
         */
        public Builder sampleSizeInBits(int sampleSizeInBits) {
            this.sampleSizeInBits = sampleSizeInBits;
            return this;
        }

        /**
         * 设置MP3比特率
         *
         * @param bitrate 比特率 (kbps)
         * @return Builder
         */
        public Builder bitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        /**
         * 设置编码质量
         *
         * @param quality 质量 (0-9, 0最好)
         * @return Builder
         */
        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        /**
         * 构建转换器实例
         *
         * @return PcmToMp3Converter实例
         */
        public PcmToMp3Converter build() {
            return new PcmToMp3Converter(sampleRate, channels, sampleSizeInBits, bitrate, quality);
        }
    }

    // Getters
    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleSizeInBits() {
        return sampleSizeInBits;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getQuality() {
        return quality;
    }
}
