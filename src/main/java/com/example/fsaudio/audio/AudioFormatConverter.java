package com.example.fsaudio.audio;

import com.example.fsaudio.config.AudioStreamConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 音频格式转换工具
 * 支持PCM与WAV之间的转换
 */
@Slf4j
@Component
public class AudioFormatConverter {

    @Autowired
    private AudioStreamConfig audioConfig;
    
    /**
     * 将PCM数据转换为WAV格式
     * 
     * @param pcmData PCM原始数据
     * @return WAV格式数据
     */
    public byte[] pcmToWav(byte[] pcmData) {
        return pcmToWav(pcmData, audioConfig.getSampleRate(), 
                audioConfig.getChannels(), audioConfig.getBitDepth());
    }
    
    /**
     * 将PCM数据转换为WAV格式
     * 
     * @param pcmData PCM原始数据
     * @param sampleRate 采样率
     * @param channels 声道数
     * @param bitDepth 位深度
     * @return WAV格式数据
     */
    public byte[] pcmToWav(byte[] pcmData, int sampleRate, int channels, int bitDepth) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            
            int byteRate = sampleRate * channels * bitDepth / 8;
            int blockAlign = channels * bitDepth / 8;
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;
            
            // RIFF头
            out.write("RIFF".getBytes());
            out.write(intToBytes(fileSize, 4));
            out.write("WAVE".getBytes());
            
            // fmt子块
            out.write("fmt ".getBytes());
            out.write(intToBytes(16, 4));           // 子块大小
            out.write(intToBytes(1, 2));            // 音频格式(1=PCM)
            out.write(intToBytes(channels, 2));     // 声道数
            out.write(intToBytes(sampleRate, 4));   // 采样率
            out.write(intToBytes(byteRate, 4));     // 字节率
            out.write(intToBytes(blockAlign, 2));   // 块对齐
            out.write(intToBytes(bitDepth, 2));     // 位深度
            
            // data子块
            out.write("data".getBytes());
            out.write(intToBytes(dataSize, 4));
            out.write(pcmData);
            
            return out.toByteArray();
            
        } catch (Exception e) {
            log.error("PCM转WAV失败", e);
            return null;
        }
    }
    
    /**
     * 从WAV数据中提取PCM数据
     * 
     * @param wavData WAV格式数据
     * @return PCM原始数据
     */
    public byte[] wavToPcm(byte[] wavData) {
        try {
            if (wavData.length < 44) {
                throw new IllegalArgumentException("WAV数据太短");
            }
            
            // 跳过WAV头(通常是44字节)
            // 但需要检查实际的data块位置
            int dataOffset = findDataChunk(wavData);
            if (dataOffset < 0) {
                throw new IllegalArgumentException("无法找到data块");
            }
            
            int dataSize = bytesToInt(wavData, dataOffset + 4, 4);
            int pcmStart = dataOffset + 8;
            
            if (pcmStart + dataSize > wavData.length) {
                dataSize = wavData.length - pcmStart;
            }
            
            byte[] pcmData = new byte[dataSize];
            System.arraycopy(wavData, pcmStart, pcmData, 0, dataSize);
            
            return pcmData;
            
        } catch (Exception e) {
            log.error("WAV转PCM失败", e);
            return null;
        }
    }
    
    /**
     * 查找data块的位置
     */
    private int findDataChunk(byte[] wavData) {
        for (int i = 0; i < wavData.length - 4; i++) {
            if (wavData[i] == 'd' && wavData[i + 1] == 'a' && 
                wavData[i + 2] == 't' && wavData[i + 3] == 'a') {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 重采样
     * 简单的线性插值重采样
     * 
     * @param pcmData 原始PCM数据
     * @param srcSampleRate 原始采样率
     * @param dstSampleRate 目标采样率
     * @return 重采样后的PCM数据
     */
    public byte[] resample(byte[] pcmData, int srcSampleRate, int dstSampleRate) {
        if (srcSampleRate == dstSampleRate) {
            return pcmData;
        }
        
        try {
            // 假设16位单声道
            int bytesPerSample = 2;
            int srcSamples = pcmData.length / bytesPerSample;
            int dstSamples = (int) ((long) srcSamples * dstSampleRate / srcSampleRate);
            
            short[] srcBuffer = new short[srcSamples];
            ByteBuffer srcBb = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < srcSamples; i++) {
                srcBuffer[i] = srcBb.getShort();
            }
            
            short[] dstBuffer = new short[dstSamples];
            double ratio = (double) srcSampleRate / dstSampleRate;
            
            for (int i = 0; i < dstSamples; i++) {
                double srcIndex = i * ratio;
                int index = (int) srcIndex;
                double fraction = srcIndex - index;
                
                if (index + 1 < srcSamples) {
                    dstBuffer[i] = (short) (srcBuffer[index] * (1 - fraction) + 
                                           srcBuffer[index + 1] * fraction);
                } else {
                    dstBuffer[i] = srcBuffer[index];
                }
            }
            
            byte[] result = new byte[dstSamples * bytesPerSample];
            ByteBuffer dstBb = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : dstBuffer) {
                dstBb.putShort(sample);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("重采样失败", e);
            return pcmData;
        }
    }
    
    /**
     * 混合两个音频流
     * 
     * @param audio1 音频数据1
     * @param audio2 音频数据2
     * @return 混合后的音频数据
     */
    public byte[] mixAudio(byte[] audio1, byte[] audio2) {
        int length = Math.min(audio1.length, audio2.length);
        byte[] result = new byte[length];
        
        ByteBuffer bb1 = ByteBuffer.wrap(audio1).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bb2 = ByteBuffer.wrap(audio2).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        
        while (bb1.remaining() >= 2 && bb2.remaining() >= 2) {
            short s1 = bb1.getShort();
            short s2 = bb2.getShort();
            
            // 简单混合，防止溢出
            int mixed = (s1 + s2) / 2;
            out.putShort((short) mixed);
        }
        
        return result;
    }
    
    /**
     * 计算音频的RMS能量
     * 
     * @param pcmData PCM数据
     * @return RMS值
     */
    public double calculateRms(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0;
        }
        
        ByteBuffer bb = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        double sum = 0;
        int count = 0;
        
        while (bb.remaining() >= 2) {
            short sample = bb.getShort();
            sum += sample * sample;
            count++;
        }
        
        return Math.sqrt(sum / count);
    }
    
    /**
     * 判断是否为静音
     * 
     * @param pcmData PCM数据
     * @param threshold 阈值
     * @return 是否为静音
     */
    public boolean isSilence(byte[] pcmData, double threshold) {
        double rms = calculateRms(pcmData);
        return rms < threshold;
    }
    
    /**
     * 整数转字节数组(小端序)
     */
    private byte[] intToBytes(int value, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((value >> (8 * i)) & 0xFF);
        }
        return bytes;
    }
    
    /**
     * 字节数组转整数(小端序)
     */
    private int bytesToInt(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
