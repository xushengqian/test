package com.example.audio;

public class AudioConfig {
    private final int sampleRate;
    private final int channels;
    private final int sampleSizeInBits;
    private final boolean bigEndian;
    private final boolean signed;

    public AudioConfig(int sampleRate, int channels, int sampleSizeInBits, boolean signed, boolean bigEndian) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.sampleSizeInBits = sampleSizeInBits;
        this.signed = signed;
        this.bigEndian = bigEndian;
    }

    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }
    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public boolean isSigned() { return signed; }
    public boolean isBigEndian() { return bigEndian; }
}
