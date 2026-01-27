package com.example.audio.converter;

import de.sciss.jump3r.lowlevel.LameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.OutputStream;

/**
 * MP3编码输出流
 * <p>
 * 包装一个输出流，将写入的PCM数据实时编码为MP3并写入底层流。
 * 适用于需要流式处理的场景，如网络传输、实时音频处理等。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * AudioConfig config = AudioConfig.voiceQuality();
 * try (OutputStream fileOut = new FileOutputStream("output.mp3");
 *      Mp3EncoderOutputStream mp3Out = new Mp3EncoderOutputStream(fileOut, config)) {
 *
 *     // 直接写入PCM数据，会自动编码为MP3
 *     while (hasMorePcmData()) {
 *         byte[] pcmChunk = getPcmChunk();
 *         mp3Out.write(pcmChunk);
 *     }
 * } // 自动调用close()完成编码
 * }</pre>
 */
public class Mp3EncoderOutputStream extends OutputStream {

    private static final Logger logger = LoggerFactory.getLogger(Mp3EncoderOutputStream.class);

    private final OutputStream outputStream;
    private final LameEncoder encoder;
    private final byte[] mp3Buffer;
    private final byte[] singleByteBuffer = new byte[1];
    private boolean closed = false;
    private long totalBytesWritten = 0;
    private long totalBytesEncoded = 0;

    /**
     * 使用默认配置创建MP3编码输出流
     *
     * @param outputStream 底层输出流
     */
    public Mp3EncoderOutputStream(OutputStream outputStream) {
        this(outputStream, new AudioConfig());
    }

    /**
     * 使用指定配置创建MP3编码输出流
     *
     * @param outputStream 底层输出流
     * @param config       音频配置
     */
    public Mp3EncoderOutputStream(OutputStream outputStream, AudioConfig config) {
        if (outputStream == null) {
            throw new IllegalArgumentException("输出流不能为null");
        }
        config.validate();

        this.outputStream = outputStream;

        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                config.getSampleRate(),
                config.getBitDepth(),
                config.getChannels(),
                config.getBytesPerSample(),
                config.getSampleRate(),
                false  // little-endian
        );

        this.encoder = new LameEncoder(
                audioFormat,
                config.getBitRate(),
                LameEncoder.CHANNEL_MODE_AUTO,
                config.getQuality(),
                false  // VBR off
        );

        this.mp3Buffer = new byte[encoder.getPCMBufferSize()];

        logger.debug("创建MP3编码输出流，配置: {}", config);
    }

    @Override
    public void write(int b) throws IOException {
        singleByteBuffer[0] = (byte) b;
        write(singleByteBuffer, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();

        if (b == null) {
            throw new NullPointerException("数据不能为null");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("无效的偏移或长度");
        }
        if (len == 0) {
            return;
        }

        totalBytesWritten += len;

        int bytesEncoded = encoder.encodeBuffer(b, off, len, mp3Buffer);
        if (bytesEncoded > 0) {
            outputStream.write(mp3Buffer, 0, bytesEncoded);
            totalBytesEncoded += bytesEncoded;
        }
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        try {
            // 刷新编码器，写入剩余数据
            int bytesEncoded = encoder.encodeFinish(mp3Buffer);
            if (bytesEncoded > 0) {
                outputStream.write(mp3Buffer, 0, bytesEncoded);
                totalBytesEncoded += bytesEncoded;
            }

            logger.debug("MP3编码完成，PCM输入: {} bytes, MP3输出: {} bytes, 压缩比: {:.2f}",
                    totalBytesWritten, totalBytesEncoded,
                    totalBytesWritten > 0 ? (double) totalBytesEncoded / totalBytesWritten : 0);
        } finally {
            closed = true;
            encoder.close();
            outputStream.close();
        }
    }

    /**
     * 获取已写入的PCM字节数
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    /**
     * 获取已编码输出的MP3字节数
     */
    public long getTotalBytesEncoded() {
        return totalBytesEncoded;
    }

    /**
     * 获取推荐的写入缓冲区大小
     */
    public int getRecommendedBufferSize() {
        return encoder.getPCMBufferSize();
    }

    /**
     * 检查流是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw new IOException("流已关闭");
        }
    }
}
