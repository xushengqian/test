package com.example.audio.converter;

/**
 * PCM和MP3音频配置类
 * <p>
 * 包含PCM输入参数和MP3输出参数的配置
 * </p>
 */
public class AudioConfig {

    /**
     * PCM输入配置
     */
    private int sampleRate;      // 采样率，如 8000, 16000, 44100, 48000
    private int channels;        // 声道数，1=单声道，2=立体声
    private int bitDepth;        // 位深度，通常为 8 或 16

    /**
     * MP3输出配置
     */
    private int bitRate;         // 比特率，如 64, 128, 192, 256, 320 kbps
    private int quality;         // 编码质量，0=最高质量(最慢)，9=最低质量(最快)

    /**
     * 默认构造函数，使用常见的默认值
     * PCM: 16000Hz, 单声道, 16位
     * MP3: 128kbps, 质量5
     */
    public AudioConfig() {
        this.sampleRate = 16000;
        this.channels = 1;
        this.bitDepth = 16;
        this.bitRate = 128;
        this.quality = 5;
    }

    /**
     * 完整参数构造函数
     *
     * @param sampleRate PCM采样率
     * @param channels   声道数
     * @param bitDepth   位深度
     * @param bitRate    MP3比特率(kbps)
     * @param quality    编码质量(0-9)
     */
    public AudioConfig(int sampleRate, int channels, int bitDepth, int bitRate, int quality) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitDepth = bitDepth;
        this.bitRate = bitRate;
        this.quality = quality;
    }

    /**
     * 创建8kHz单声道16位PCM配置（电话质量）
     */
    public static AudioConfig telephoneQuality() {
        return new AudioConfig(8000, 1, 16, 64, 5);
    }

    /**
     * 创建16kHz单声道16位PCM配置（语音质量）
     */
    public static AudioConfig voiceQuality() {
        return new AudioConfig(16000, 1, 16, 128, 5);
    }

    /**
     * 创建44100Hz立体声16位PCM配置（CD质量）
     */
    public static AudioConfig cdQuality() {
        return new AudioConfig(44100, 2, 16, 320, 2);
    }

    /**
     * 创建48000Hz立体声16位PCM配置（专业质量）
     */
    public static AudioConfig professionalQuality() {
        return new AudioConfig(48000, 2, 16, 320, 0);
    }

    // Getters and Setters

    public int getSampleRate() {
        return sampleRate;
    }

    public AudioConfig setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    public int getChannels() {
        return channels;
    }

    public AudioConfig setChannels(int channels) {
        this.channels = channels;
        return this;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public AudioConfig setBitDepth(int bitDepth) {
        this.bitDepth = bitDepth;
        return this;
    }

    public int getBitRate() {
        return bitRate;
    }

    public AudioConfig setBitRate(int bitRate) {
        this.bitRate = bitRate;
        return this;
    }

    public int getQuality() {
        return quality;
    }

    public AudioConfig setQuality(int quality) {
        this.quality = quality;
        return this;
    }

    /**
     * 计算每个采样的字节数
     */
    public int getBytesPerSample() {
        return (bitDepth / 8) * channels;
    }

    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("采样率必须大于0");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("声道数必须是1（单声道）或2（立体声）");
        }
        if (bitDepth != 8 && bitDepth != 16) {
            throw new IllegalArgumentException("位深度必须是8或16");
        }
        if (bitRate <= 0) {
            throw new IllegalArgumentException("比特率必须大于0");
        }
        if (quality < 0 || quality > 9) {
            throw new IllegalArgumentException("质量必须在0-9之间");
        }
    }

    @Override
    public String toString() {
        return "AudioConfig{" +
                "sampleRate=" + sampleRate +
                ", channels=" + channels +
                ", bitDepth=" + bitDepth +
                ", bitRate=" + bitRate +
                ", quality=" + quality +
                '}';
    }
}
