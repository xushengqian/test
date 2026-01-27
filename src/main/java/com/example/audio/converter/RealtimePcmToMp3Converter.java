package com.example.audio.converter;

import de.sciss.jump3r.lowlevel.LameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 实时PCM到MP3流转换器
 * <p>
 * 专为实时音频流场景设计，支持：
 * <ul>
 *     <li>异步接收PCM数据</li>
 *     <li>回调方式输出MP3数据</li>
 *     <li>线程安全的数据输入</li>
 *     <li>自动缓冲和编码</li>
 * </ul>
 * </p>
 *
 * <p>使用示例（WebSocket场景）：</p>
 * <pre>{@code
 * AudioConfig config = AudioConfig.voiceQuality();
 * RealtimePcmToMp3Converter converter = new RealtimePcmToMp3Converter(config, mp3Data -> {
 *     // 将MP3数据发送给客户端
 *     webSocket.sendBinary(mp3Data);
 * });
 *
 * converter.start();
 *
 * // 在WebSocket消息处理中
 * webSocket.onMessage(pcmData -> {
 *     converter.feed(pcmData);
 * });
 *
 * // 关闭时
 * converter.stop();
 * }</pre>
 */
public class RealtimePcmToMp3Converter implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(RealtimePcmToMp3Converter.class);

    private final AudioConfig config;
    private final Consumer<byte[]> mp3Consumer;
    private final BlockingQueue<byte[]> pcmQueue;
    private final int minBufferSize;

    private volatile boolean running = false;
    private volatile boolean stopped = false;
    private Thread encoderThread;

    private long totalPcmBytes = 0;
    private long totalMp3Bytes = 0;

    /**
     * 创建实时转换器
     *
     * @param config      音频配置
     * @param mp3Consumer MP3数据消费者回调
     */
    public RealtimePcmToMp3Converter(AudioConfig config, Consumer<byte[]> mp3Consumer) {
        this(config, mp3Consumer, 100);
    }

    /**
     * 创建实时转换器
     *
     * @param config        音频配置
     * @param mp3Consumer   MP3数据消费者回调
     * @param queueCapacity PCM数据队列容量
     */
    public RealtimePcmToMp3Converter(AudioConfig config, Consumer<byte[]> mp3Consumer, int queueCapacity) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为null");
        }
        if (mp3Consumer == null) {
            throw new IllegalArgumentException("MP3消费者不能为null");
        }
        config.validate();

        this.config = config;
        this.mp3Consumer = mp3Consumer;
        this.pcmQueue = new LinkedBlockingQueue<>(queueCapacity);

        // 计算最小缓冲大小（约100ms的数据）
        this.minBufferSize = config.getSampleRate() * config.getBytesPerSample() / 10;

        logger.debug("创建实时PCM到MP3转换器，配置: {}", config);
    }

    /**
     * 启动转换器
     */
    public synchronized void start() {
        if (running) {
            logger.warn("转换器已在运行中");
            return;
        }
        if (stopped) {
            throw new IllegalStateException("转换器已停止，无法重新启动");
        }

        running = true;
        encoderThread = new Thread(this::encodingLoop, "RealtimeMp3Encoder");
        encoderThread.setDaemon(true);
        encoderThread.start();

        logger.info("实时转换器已启动");
    }

    /**
     * 输入PCM数据
     * <p>
     * 此方法是线程安全的，可以从多个线程调用
     * </p>
     *
     * @param pcmData PCM数据
     * @return true如果数据被成功加入队列，false如果队列已满或转换器未运行
     */
    public boolean feed(byte[] pcmData) {
        if (!running || pcmData == null || pcmData.length == 0) {
            return false;
        }

        return pcmQueue.offer(pcmData);
    }

    /**
     * 输入PCM数据（阻塞方式）
     *
     * @param pcmData PCM数据
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true如果数据被成功加入队列
     * @throws InterruptedException 如果等待被中断
     */
    public boolean feed(byte[] pcmData, long timeout, TimeUnit unit) throws InterruptedException {
        if (!running || pcmData == null || pcmData.length == 0) {
            return false;
        }

        return pcmQueue.offer(pcmData, timeout, unit);
    }

    /**
     * 停止转换器
     * <p>
     * 会等待队列中剩余的数据处理完成
     * </p>
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        stopped = true;

        if (encoderThread != null) {
            encoderThread.interrupt();
            try {
                encoderThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("实时转换器已停止，总计处理: PCM {} bytes -> MP3 {} bytes",
                totalPcmBytes, totalMp3Bytes);
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * 检查转换器是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取队列中待处理的数据块数量
     */
    public int getPendingChunks() {
        return pcmQueue.size();
    }

    /**
     * 获取已处理的PCM字节数
     */
    public long getTotalPcmBytes() {
        return totalPcmBytes;
    }

    /**
     * 获取已输出的MP3字节数
     */
    public long getTotalMp3Bytes() {
        return totalMp3Bytes;
    }

    private void encodingLoop() {
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                config.getSampleRate(),
                config.getBitDepth(),
                config.getChannels(),
                config.getBytesPerSample(),
                config.getSampleRate(),
                false
        );

        LameEncoder encoder = new LameEncoder(
                audioFormat,
                config.getBitRate(),
                LameEncoder.CHANNEL_MODE_AUTO,
                config.getQuality(),
                false
        );

        byte[] mp3Buffer = new byte[encoder.getPCMBufferSize()];
        ByteArrayOutputStream pcmAccumulator = new ByteArrayOutputStream();

        try {
            while (running || !pcmQueue.isEmpty()) {
                byte[] pcmData = pcmQueue.poll(100, TimeUnit.MILLISECONDS);

                if (pcmData != null) {
                    pcmAccumulator.write(pcmData);
                    totalPcmBytes += pcmData.length;

                    // 当积累足够数据时进行编码
                    if (pcmAccumulator.size() >= minBufferSize) {
                        encodeAndSend(encoder, pcmAccumulator.toByteArray(), mp3Buffer);
                        pcmAccumulator.reset();
                    }
                }
            }

            // 处理剩余数据
            if (pcmAccumulator.size() > 0) {
                encodeAndSend(encoder, pcmAccumulator.toByteArray(), mp3Buffer);
            }

            // 刷新编码器
            int remaining = encoder.encodeFinish(mp3Buffer);
            if (remaining > 0) {
                byte[] finalMp3 = new byte[remaining];
                System.arraycopy(mp3Buffer, 0, finalMp3, 0, remaining);
                totalMp3Bytes += remaining;
                mp3Consumer.accept(finalMp3);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("编码线程被中断");
        } catch (Exception e) {
            logger.error("编码过程中发生错误", e);
        } finally {
            encoder.close();
        }
    }

    private void encodeAndSend(LameEncoder encoder, byte[] pcmData, byte[] mp3Buffer) {
        int offset = 0;
        while (offset < pcmData.length) {
            int chunkSize = Math.min(encoder.getPCMBufferSize(), pcmData.length - offset);
            int encoded = encoder.encodeBuffer(pcmData, offset, chunkSize, mp3Buffer);

            if (encoded > 0) {
                byte[] mp3Chunk = new byte[encoded];
                System.arraycopy(mp3Buffer, 0, mp3Chunk, 0, encoded);
                totalMp3Bytes += encoded;
                mp3Consumer.accept(mp3Chunk);
            }

            offset += chunkSize;
        }
    }
}
