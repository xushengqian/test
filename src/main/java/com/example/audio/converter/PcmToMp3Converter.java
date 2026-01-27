package com.example.audio.converter;

import de.sciss.jump3r.lowlevel.LameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.*;

/**
 * PCM音频流转MP3音频流转换器
 * <p>
 * 支持以下功能：
 * <ul>
 *     <li>PCM字节数组转MP3字节数组</li>
 *     <li>PCM输入流转MP3输出流</li>
 *     <li>PCM文件转MP3文件</li>
 *     <li>流式增量转换</li>
 * </ul>
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 方式1：字节数组转换
 * AudioConfig config = AudioConfig.voiceQuality();
 * PcmToMp3Converter converter = new PcmToMp3Converter(config);
 * byte[] mp3Data = converter.convert(pcmData);
 *
 * // 方式2：流式转换
 * try (InputStream pcmInput = new FileInputStream("audio.pcm");
 *      OutputStream mp3Output = new FileOutputStream("audio.mp3")) {
 *     converter.convert(pcmInput, mp3Output);
 * }
 *
 * // 方式3：增量转换（适用于实时音频流）
 * PcmToMp3Converter.StreamEncoder encoder = converter.createStreamEncoder();
 * while (hasMorePcmData()) {
 *     byte[] pcmChunk = getPcmChunk();
 *     byte[] mp3Chunk = encoder.encode(pcmChunk);
 *     sendMp3Chunk(mp3Chunk);
 * }
 * byte[] finalMp3 = encoder.flush();
 * sendMp3Chunk(finalMp3);
 * encoder.close();
 * }</pre>
 */
public class PcmToMp3Converter {

    private static final Logger logger = LoggerFactory.getLogger(PcmToMp3Converter.class);

    private final AudioConfig config;

    /**
     * 使用默认配置创建转换器
     */
    public PcmToMp3Converter() {
        this(new AudioConfig());
    }

    /**
     * 使用指定配置创建转换器
     *
     * @param config 音频配置
     */
    public PcmToMp3Converter(AudioConfig config) {
        config.validate();
        this.config = config;
        logger.debug("创建PCM到MP3转换器，配置: {}", config);
    }

    /**
     * 将PCM字节数组转换为MP3字节数组
     *
     * @param pcmData PCM音频数据
     * @return MP3音频数据
     * @throws IOException 如果转换过程中发生IO错误
     */
    public byte[] convert(byte[] pcmData) throws IOException {
        if (pcmData == null || pcmData.length == 0) {
            return new byte[0];
        }

        try (ByteArrayInputStream pcmInput = new ByteArrayInputStream(pcmData);
             ByteArrayOutputStream mp3Output = new ByteArrayOutputStream()) {
            convert(pcmInput, mp3Output);
            return mp3Output.toByteArray();
        }
    }

    /**
     * 将PCM输入流转换为MP3输出流
     *
     * @param pcmInput  PCM输入流
     * @param mp3Output MP3输出流
     * @throws IOException 如果转换过程中发生IO错误
     */
    public void convert(InputStream pcmInput, OutputStream mp3Output) throws IOException {
        LameEncoder encoder = createEncoder();

        try {
            byte[] pcmBuffer = new byte[encoder.getPCMBufferSize()];
            byte[] mp3Buffer = new byte[encoder.getPCMBufferSize()];

            int bytesRead;
            while ((bytesRead = pcmInput.read(pcmBuffer)) > 0) {
                int bytesEncoded = encoder.encodeBuffer(pcmBuffer, 0, bytesRead, mp3Buffer);
                if (bytesEncoded > 0) {
                    mp3Output.write(mp3Buffer, 0, bytesEncoded);
                }
            }

            // 刷新编码器，获取剩余数据
            int bytesEncoded = encoder.encodeFinish(mp3Buffer);
            if (bytesEncoded > 0) {
                mp3Output.write(mp3Buffer, 0, bytesEncoded);
            }

            logger.debug("PCM到MP3转换完成");
        } finally {
            encoder.close();
        }
    }

    /**
     * 将PCM文件转换为MP3文件
     *
     * @param pcmFile PCM输入文件
     * @param mp3File MP3输出文件
     * @throws IOException 如果转换过程中发生IO错误
     */
    public void convertFile(File pcmFile, File mp3File) throws IOException {
        logger.info("转换文件: {} -> {}", pcmFile.getAbsolutePath(), mp3File.getAbsolutePath());

        try (InputStream pcmInput = new BufferedInputStream(new FileInputStream(pcmFile));
             OutputStream mp3Output = new BufferedOutputStream(new FileOutputStream(mp3File))) {
            convert(pcmInput, mp3Output);
        }

        logger.info("文件转换完成，输出文件大小: {} bytes", mp3File.length());
    }

    /**
     * 将PCM文件转换为MP3文件（使用路径字符串）
     *
     * @param pcmFilePath PCM输入文件路径
     * @param mp3FilePath MP3输出文件路径
     * @throws IOException 如果转换过程中发生IO错误
     */
    public void convertFile(String pcmFilePath, String mp3FilePath) throws IOException {
        convertFile(new File(pcmFilePath), new File(mp3FilePath));
    }

    /**
     * 创建流式编码器，用于增量转换
     * <p>
     * 适用于实时音频流场景，可以逐块编码PCM数据
     * </p>
     *
     * @return 流式编码器实例
     */
    public StreamEncoder createStreamEncoder() {
        return new StreamEncoder(createEncoder());
    }

    /**
     * 创建LAME编码器实例
     */
    private LameEncoder createEncoder() {
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                config.getSampleRate(),
                config.getBitDepth(),
                config.getChannels(),
                config.getBytesPerSample(),
                config.getSampleRate(),
                false  // little-endian
        );

        return new LameEncoder(
                audioFormat,
                config.getBitRate(),
                LameEncoder.CHANNEL_MODE_AUTO,
                config.getQuality(),
                false  // VBR off
        );
    }

    /**
     * 获取当前配置
     */
    public AudioConfig getConfig() {
        return config;
    }

    /**
     * 流式编码器，支持增量编码PCM数据
     * <p>
     * 使用完毕后必须调用 {@link #close()} 方法释放资源
     * </p>
     */
    public static class StreamEncoder implements Closeable {

        private final LameEncoder encoder;
        private final byte[] mp3Buffer;
        private boolean closed = false;

        private StreamEncoder(LameEncoder encoder) {
            this.encoder = encoder;
            this.mp3Buffer = new byte[encoder.getPCMBufferSize()];
        }

        /**
         * 编码PCM数据块
         *
         * @param pcmData PCM数据
         * @return 编码后的MP3数据（可能为空数组）
         * @throws IOException          如果编码过程中发生错误
         * @throws IllegalStateException 如果编码器已关闭
         */
        public byte[] encode(byte[] pcmData) throws IOException {
            return encode(pcmData, 0, pcmData.length);
        }

        /**
         * 编码PCM数据块（指定范围）
         *
         * @param pcmData PCM数据
         * @param offset  起始偏移
         * @param length  数据长度
         * @return 编码后的MP3数据（可能为空数组）
         * @throws IOException          如果编码过程中发生错误
         * @throws IllegalStateException 如果编码器已关闭
         */
        public byte[] encode(byte[] pcmData, int offset, int length) throws IOException {
            checkClosed();

            if (pcmData == null || length == 0) {
                return new byte[0];
            }

            int bytesEncoded = encoder.encodeBuffer(pcmData, offset, length, mp3Buffer);
            if (bytesEncoded > 0) {
                byte[] result = new byte[bytesEncoded];
                System.arraycopy(mp3Buffer, 0, result, 0, bytesEncoded);
                return result;
            }
            return new byte[0];
        }

        /**
         * 刷新编码器，获取剩余的MP3数据
         * <p>
         * 在所有PCM数据编码完成后调用此方法，获取编码器缓冲区中剩余的数据
         * </p>
         *
         * @return 剩余的MP3数据
         * @throws IOException          如果编码过程中发生错误
         * @throws IllegalStateException 如果编码器已关闭
         */
        public byte[] flush() throws IOException {
            checkClosed();

            int bytesEncoded = encoder.encodeFinish(mp3Buffer);
            if (bytesEncoded > 0) {
                byte[] result = new byte[bytesEncoded];
                System.arraycopy(mp3Buffer, 0, result, 0, bytesEncoded);
                return result;
            }
            return new byte[0];
        }

        /**
         * 获取推荐的PCM缓冲区大小
         */
        public int getRecommendedBufferSize() {
            return encoder.getPCMBufferSize();
        }

        /**
         * 检查编码器是否已关闭
         */
        public boolean isClosed() {
            return closed;
        }

        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("编码器已关闭");
            }
        }

        @Override
        public void close() {
            if (!closed) {
                encoder.close();
                closed = true;
            }
        }
    }
}
