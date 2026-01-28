package com.example.audio;

import de.sciss.jump3r.lowlevel.LameEncoder;
import de.sciss.jump3r.mp3.Lame;
import de.sciss.jump3r.mp3.Version;

import java.io.*;

public class PcmToMp3Converter {

    /**
     * 将 PCM 文件转换为 MP3 文件
     *
     * @param pcmFile       PCM 源文件
     * @param mp3File       MP3 目标文件
     * @param sampleRate    采样率 (例如 44100)
     * @param channels      通道数 (例如 1 或 2)
     * @param bitRate       比特率 (例如 128)
     * @throws IOException  IO 异常
     */
    public static void convertPcmToMp3(File pcmFile, File mp3File, int sampleRate, int channels, int bitRate) throws IOException {
        // Lame 参数初始化
        LameEncoder encoder = new LameEncoder(new javax.sound.sampled.AudioFormat(sampleRate, 16, channels, true, false), bitRate, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false);

        try (InputStream in = new BufferedInputStream(new FileInputStream(pcmFile));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(mp3File))) {

            byte[] buffer = new byte[4096 * 2]; // 缓冲区大小
            byte[] mp3Buffer = new byte[buffer.length]; // MP3 缓冲区
            
            int bytesRead;
            while ((bytesRead = in.read(buffer)) > 0) {
                int bytesWritten = encoder.encodeBuffer(buffer, 0, bytesRead, mp3Buffer);
                if (bytesWritten > 0) {
                    out.write(mp3Buffer, 0, bytesWritten);
                }
            }

            // 写入剩余的 MP3 数据
            int bytesWritten = encoder.encodeFinish(mp3Buffer);
            if (bytesWritten > 0) {
                out.write(mp3Buffer, 0, bytesWritten);
            }
            
            encoder.close();
        }
    }
}
