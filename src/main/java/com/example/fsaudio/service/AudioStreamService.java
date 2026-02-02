package com.example.fsaudio.service;

import com.example.fsaudio.config.AudioStreamConfig;
import com.example.fsaudio.esl.EslConnectionManager;
import com.example.fsaudio.handler.EslEventHandler;
import com.example.fsaudio.model.AudioFrame;
import com.example.fsaudio.model.StreamRequest;
import com.example.fsaudio.websocket.AudioWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

/**
 * 音频流服务
 * 
 * 核心功能:
 * 1. 启动/停止音频流捕获
 * 2. 处理实时音频数据
 * 3. 通过WebSocket推送音频
 * 4. 保存音频到文件
 */
@Slf4j
@Service
public class AudioStreamService {

    private final EslConnectionManager eslManager;
    private final EslEventHandler eventHandler;
    private final AudioStreamConfig config;
    private final AudioWebSocketHandler webSocketHandler;

    /**
     * 音频数据处理队列
     */
    private final ConcurrentHashMap<String, BlockingQueue<AudioFrame>> audioQueues = new ConcurrentHashMap<>();

    /**
     * 音频文件输出流
     */
    private final ConcurrentHashMap<String, FileOutputStream> audioFileStreams = new ConcurrentHashMap<>();

    /**
     * 音频处理线程池
     */
    private ExecutorService audioProcessorPool;

    /**
     * 活跃的音频流任务
     */
    private final ConcurrentHashMap<String, Future<?>> streamTasks = new ConcurrentHashMap<>();

    @Autowired
    public AudioStreamService(EslConnectionManager eslManager, 
                             EslEventHandler eventHandler,
                             AudioStreamConfig config,
                             AudioWebSocketHandler webSocketHandler) {
        this.eslManager = eslManager;
        this.eventHandler = eventHandler;
        this.config = config;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void init() {
        audioProcessorPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "audio-processor");
            t.setDaemon(true);
            return t;
        });

        // 创建音频保存目录
        try {
            Path audioPath = Paths.get(config.getSavePath());
            if (!Files.exists(audioPath)) {
                Files.createDirectories(audioPath);
                log.info("创建音频保存目录: {}", config.getSavePath());
            }
        } catch (IOException e) {
            log.error("创建音频保存目录失败: {}", e.getMessage());
        }
    }

    /**
     * 启动音频流捕获
     * 
     * @param request 流请求参数
     * @return 是否成功
     */
    public boolean startAudioStream(StreamRequest request) {
        String uuid = request.getUuid();
        
        if (!eslManager.isConnected()) {
            log.error("ESL未连接，无法启动音频流: {}", uuid);
            return false;
        }

        if (streamTasks.containsKey(uuid)) {
            log.warn("音频流已存在: {}", uuid);
            return false;
        }

        try {
            // 创建音频队列
            BlockingQueue<AudioFrame> queue = new LinkedBlockingQueue<>(1000);
            audioQueues.put(uuid, queue);

            // 方法1: 使用uuid_audio_stream命令 (需要mod_audio_stream模块)
            // 这种方式可以实时获取音频数据并通过WebSocket推送
            String streamUrl = request.getWebsocketUrl();
            if (streamUrl != null && !streamUrl.isEmpty()) {
                String command = String.format("uuid_audio_stream %s start %s both", uuid, streamUrl);
                String response = eslManager.executeApi(command);
                log.info("启动音频流命令响应: {}", response);
            }

            // 方法2: 使用uuid_buglist/uuid_record进行录音
            // 如果需要保存到文件
            if (request.isSaveToFile()) {
                startFileRecording(uuid, request.getDirection());
            }

            // 方法3: 使用media_bug捕获音频 (通过mod_oreka或自定义模块)
            // 启动媒体嗅探
            startMediaBug(uuid, request.getDirection());

            // 标记会话音频流已激活
            eventHandler.setAudioStreamActive(uuid, true);

            // 启动音频处理任务
            Future<?> task = audioProcessorPool.submit(() -> processAudioQueue(uuid));
            streamTasks.put(uuid, task);

            log.info("音频流已启动: {}", uuid);
            return true;

        } catch (Exception e) {
            log.error("启动音频流失败: {} - {}", uuid, e.getMessage());
            return false;
        }
    }

    /**
     * 启动音频流捕获 (简化版)
     */
    public boolean startAudioStream(String uuid) {
        StreamRequest request = StreamRequest.builder()
                .uuid(uuid)
                .direction("both")
                .saveToFile(true)
                .build();
        return startAudioStream(request);
    }

    /**
     * 停止音频流捕获
     */
    public void stopAudioStream(String uuid) {
        log.info("停止音频流: {}", uuid);

        try {
            // 停止音频流命令
            String command = String.format("uuid_audio_stream %s stop", uuid);
            eslManager.executeApi(command);

            // 停止媒体嗅探
            stopMediaBug(uuid);

            // 停止文件录音
            stopFileRecording(uuid);

            // 取消处理任务
            Future<?> task = streamTasks.remove(uuid);
            if (task != null) {
                task.cancel(true);
            }

            // 清理队列
            audioQueues.remove(uuid);

            // 标记会话音频流已停止
            eventHandler.setAudioStreamActive(uuid, false);

        } catch (Exception e) {
            log.error("停止音频流失败: {} - {}", uuid, e.getMessage());
        }
    }

    /**
     * 启动媒体嗅探 (使用FreeSwtich的media_bug功能)
     */
    private void startMediaBug(String uuid, String direction) {
        // 使用uuid_buglist添加媒体嗅探
        // direction: read=入方向, write=出方向, both=双向
        String bugDirection = "both".equals(direction) ? "rw" : 
                             "in".equals(direction) ? "r" : "w";

        // 方式1: 使用eavesdrop进行监听
        // String command = String.format("uuid_broadcast %s eavesdrop::%s", uuid, targetUuid);

        // 方式2: 使用session_audio进行音频捕获
        // 需要配合Lua脚本或自定义应用处理

        // 方式3: 使用record进行临时录音
        String recordPath = config.getSavePath() + "/" + uuid + "_stream.raw";
        String command = String.format("uuid_record %s start %s", uuid, recordPath);
        String response = eslManager.executeApi(command);
        log.debug("启动媒体嗅探响应: {}", response);
    }

    /**
     * 停止媒体嗅探
     */
    private void stopMediaBug(String uuid) {
        String recordPath = config.getSavePath() + "/" + uuid + "_stream.raw";
        String command = String.format("uuid_record %s stop %s", uuid, recordPath);
        eslManager.executeApi(command);
    }

    /**
     * 启动文件录音
     */
    private void startFileRecording(String uuid, String direction) {
        try {
            String filename = String.format("%s/%s_%s.pcm", 
                    config.getSavePath(), uuid, System.currentTimeMillis());
            FileOutputStream fos = new FileOutputStream(filename);
            audioFileStreams.put(uuid, fos);
            log.info("开始录音到文件: {}", filename);
        } catch (FileNotFoundException e) {
            log.error("创建录音文件失败: {}", e.getMessage());
        }
    }

    /**
     * 停止文件录音
     */
    private void stopFileRecording(String uuid) {
        FileOutputStream fos = audioFileStreams.remove(uuid);
        if (fos != null) {
            try {
                fos.flush();
                fos.close();
                log.info("录音文件已保存: {}", uuid);
            } catch (IOException e) {
                log.error("关闭录音文件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 处理音频数据 (从ESL事件接收)
     */
    public void processAudioData(String uuid, byte[] data, String direction, 
                                 long timestamp, long sequence) {
        // 构建音频帧
        AudioFrame frame = AudioFrame.builder()
                .uuid(uuid)
                .data(data)
                .timestamp(timestamp)
                .sampleRate(config.getSampleRate())
                .channels(config.getChannels())
                .direction(direction)
                .sequenceNumber(sequence)
                .build();

        // 放入处理队列
        BlockingQueue<AudioFrame> queue = audioQueues.get(uuid);
        if (queue != null) {
            if (!queue.offer(frame)) {
                log.warn("音频队列已满，丢弃数据帧: {} seq={}", uuid, sequence);
            }
        }

        // 同时写入文件
        FileOutputStream fos = audioFileStreams.get(uuid);
        if (fos != null) {
            try {
                fos.write(data);
            } catch (IOException e) {
                log.error("写入音频文件失败: {}", e.getMessage());
            }
        }

        // 通过WebSocket推送
        if (config.isWebsocketEnabled()) {
            webSocketHandler.sendAudioFrame(uuid, frame);
        }
    }

    /**
     * 处理音频队列
     */
    private void processAudioQueue(String uuid) {
        BlockingQueue<AudioFrame> queue = audioQueues.get(uuid);
        if (queue == null) {
            return;
        }

        log.info("音频处理任务启动: {}", uuid);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                AudioFrame frame = queue.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null) {
                    // 处理音频帧
                    handleAudioFrame(frame);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理音频帧异常: {}", e.getMessage());
            }
        }

        log.info("音频处理任务结束: {}", uuid);
    }

    /**
     * 处理单个音频帧
     */
    private void handleAudioFrame(AudioFrame frame) {
        // 这里可以添加音频处理逻辑:
        // 1. 音频转码
        // 2. VAD (语音活动检测)
        // 3. ASR (语音识别)
        // 4. 音频增强
        
        log.trace("处理音频帧: uuid={} seq={} size={}", 
                frame.getUuid(), frame.getSequenceNumber(), frame.getData().length);
    }

    /**
     * 获取通话的音频数据 (用于API获取)
     */
    public byte[] getAudioData(String uuid, int maxFrames) {
        BlockingQueue<AudioFrame> queue = audioQueues.get(uuid);
        if (queue == null || queue.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count = 0;
        
        while (!queue.isEmpty() && count < maxFrames) {
            AudioFrame frame = queue.poll();
            if (frame != null) {
                try {
                    baos.write(frame.getData());
                    count++;
                } catch (IOException e) {
                    log.error("合并音频数据失败: {}", e.getMessage());
                }
            }
        }

        return baos.toByteArray();
    }

    /**
     * 获取当前活跃的音频流列表
     */
    public java.util.Set<String> getActiveStreams() {
        return new java.util.HashSet<>(streamTasks.keySet());
    }

    /**
     * 检查音频流是否活跃
     */
    public boolean isStreamActive(String uuid) {
        return streamTasks.containsKey(uuid);
    }
}
