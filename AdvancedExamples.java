import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 高级示例 - 处理各种实际场景
 */
public class AdvancedExamples {

    /**
     * 示例1: 带进度监控的文件上传
     */
    public static class ProgressUploader {
        
        public interface ProgressCallback {
            void onProgress(long bytesWritten, long totalBytes);
        }
        
        public static String uploadWithProgress(String uploadUrl, byte[] fileBytes,
                                               String fileName, String fieldName,
                                               ProgressCallback callback) throws IOException {
            String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
            String CRLF = "\r\n";
            
            URL url = new URL(uploadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            // 计算总大小
            long totalSize = fileBytes.length + 500; // 加上头部和尾部的大小
            long bytesWritten = 0;
            
            try (OutputStream output = connection.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
                
                // 写入文件头部
                writer.append("--").append(boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                      .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
                writer.append("Content-Type: application/octet-stream").append(CRLF);
                writer.append(CRLF);
                writer.flush();
                
                // 分块写入文件数据，每次回调进度
                int chunkSize = 8192; // 8KB per chunk
                int offset = 0;
                while (offset < fileBytes.length) {
                    int length = Math.min(chunkSize, fileBytes.length - offset);
                    output.write(fileBytes, offset, length);
                    output.flush();
                    
                    offset += length;
                    bytesWritten = offset;
                    
                    if (callback != null) {
                        callback.onProgress(bytesWritten, fileBytes.length);
                    }
                }
                
                // 写入结束标记
                writer.append(CRLF);
                writer.append("--").append(boundary).append("--").append(CRLF);
                writer.flush();
            }
            
            // 读取响应
            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            
            connection.disconnect();
            return "状态码: " + responseCode + "\n" + response.toString();
        }
    }

    /**
     * 示例2: 带重试机制的上传
     */
    public static class RetryUploader {
        
        public static String uploadWithRetry(String uploadUrl, byte[] fileBytes,
                                            String fileName, String fieldName,
                                            int maxRetries) {
            int attempt = 0;
            Exception lastException = null;
            
            while (attempt < maxRetries) {
                try {
                    System.out.println("尝试上传 (第 " + (attempt + 1) + " 次)...");
                    
                    String result = FileUploadUtils.uploadBytes(
                        uploadUrl, fileBytes, fileName, fieldName, null
                    ).toString();
                    
                    System.out.println("上传成功！");
                    return result;
                    
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    
                    if (attempt < maxRetries) {
                        System.out.println("上传失败，等待重试...");
                        try {
                            Thread.sleep(2000 * attempt); // 递增等待时间
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            
            return "上传失败，已重试 " + maxRetries + " 次。最后错误: " + 
                   (lastException != null ? lastException.getMessage() : "未知错误");
        }
    }

    /**
     * 示例3: 大文件分片上传
     */
    public static class ChunkedUploader {
        
        public static String uploadLargeFile(String uploadUrl, byte[] fileBytes,
                                            String fileName, String fieldName,
                                            int chunkSize) throws IOException {
            int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
            String uploadId = UUID.randomUUID().toString();
            
            System.out.println("开始分片上传: " + fileName);
            System.out.println("总大小: " + fileBytes.length + " bytes");
            System.out.println("分片大小: " + chunkSize + " bytes");
            System.out.println("分片数量: " + totalChunks);
            System.out.println();
            
            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileBytes.length);
                byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);
                
                // 上传分片
                Map<String, String> params = new HashMap<>();
                params.put("uploadId", uploadId);
                params.put("chunkIndex", String.valueOf(i));
                params.put("totalChunks", String.valueOf(totalChunks));
                params.put("originalFileName", fileName);
                
                String chunkFileName = fileName + ".part" + i;
                
                System.out.print("上传分片 " + (i + 1) + "/" + totalChunks + " ... ");
                
                FileUploadUtils.UploadResult result = FileUploadUtils.uploadBytes(
                    uploadUrl, chunk, chunkFileName, fieldName, params
                );
                
                if (result.success) {
                    System.out.println("✅");
                } else {
                    System.out.println("❌");
                    throw new IOException("分片 " + i + " 上传失败");
                }
            }
            
            System.out.println("\n✅ 所有分片上传完成！");
            return "文件 " + fileName + " 已分 " + totalChunks + " 片上传完成";
        }
    }

    /**
     * 示例4: 压缩后上传（减少传输大小）
     */
    public static class CompressedUploader {
        
        public static String uploadCompressed(String uploadUrl, byte[] fileBytes,
                                             String fileName, String fieldName) throws IOException {
            // 压缩文件
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos)) {
                gzipOut.write(fileBytes);
            }
            byte[] compressedBytes = baos.toByteArray();
            
            System.out.println("原始大小: " + fileBytes.length + " bytes");
            System.out.println("压缩后大小: " + compressedBytes.length + " bytes");
            System.out.println("压缩率: " + String.format("%.2f%%", 
                (1 - (double) compressedBytes.length / fileBytes.length) * 100));
            
            // 上传压缩文件
            Map<String, String> params = new HashMap<>();
            params.put("compressed", "true");
            params.put("originalSize", String.valueOf(fileBytes.length));
            params.put("compressionType", "gzip");
            
            return FileUploadUtils.uploadBytes(
                uploadUrl, 
                compressedBytes, 
                fileName + ".gz", 
                fieldName, 
                params
            ).toString();
        }
    }

    /**
     * 示例5: 多文件并发上传
     */
    public static class ConcurrentUploader {
        
        public static void uploadConcurrently(String uploadUrl, 
                                             List<FileUploadUtils.FileData> files,
                                             String fieldName) {
            System.out.println("开始并发上传 " + files.size() + " 个文件...\n");
            
            // 使用线程池
            int threadCount = Math.min(files.size(), 5); // 最多5个并发
            List<Thread> threads = new ArrayList<>();
            
            for (int i = 0; i < files.size(); i++) {
                final FileUploadUtils.FileData file = files.get(i);
                final int index = i;
                
                Thread thread = new Thread(() -> {
                    try {
                        System.out.println("[线程 " + Thread.currentThread().getId() + "] " +
                                         "开始上传: " + file.fileName);
                        
                        FileUploadUtils.UploadResult result = FileUploadUtils.uploadBytes(
                            uploadUrl, file.data, file.fileName, fieldName, null
                        );
                        
                        if (result.success) {
                            System.out.println("[线程 " + Thread.currentThread().getId() + "] " +
                                             "✅ 成功: " + file.fileName);
                        } else {
                            System.out.println("[线程 " + Thread.currentThread().getId() + "] " +
                                             "❌ 失败: " + file.fileName);
                        }
                    } catch (Exception e) {
                        System.out.println("[线程 " + Thread.currentThread().getId() + "] " +
                                         "❌ 异常: " + file.fileName + " - " + e.getMessage());
                    }
                });
                
                threads.add(thread);
                thread.start();
                
                // 控制并发数
                if (threads.size() >= threadCount) {
                    for (Thread t : threads) {
                        try {
                            t.join();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    threads.clear();
                }
            }
            
            // 等待所有线程完成
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            System.out.println("\n✅ 所有文件上传完成！");
        }
    }

    /**
     * 示例6: 断点续传
     */
    public static class ResumableUploader {
        
        // 保存上传进度的状态文件
        private static final String PROGRESS_DIR = ".upload_progress";
        
        public static String uploadResumable(String uploadUrl, byte[] fileBytes,
                                            String fileName, String fieldName) throws IOException {
            // 创建进度目录
            Path progressDir = Paths.get(PROGRESS_DIR);
            if (!Files.exists(progressDir)) {
                Files.createDirectories(progressDir);
            }
            
            // 生成上传ID
            String uploadId = UUID.randomUUID().toString();
            Path progressFile = progressDir.resolve(uploadId + ".json");
            
            System.out.println("上传ID: " + uploadId);
            System.out.println("文件: " + fileName + " (" + fileBytes.length + " bytes)");
            
            // 分片上传
            int chunkSize = 1024 * 1024; // 1MB
            int totalChunks = (int) Math.ceil((double) fileBytes.length / chunkSize);
            int startChunk = 0;
            
            // 检查是否有之前的进度
            if (Files.exists(progressFile)) {
                System.out.println("发现之前的上传进度，继续上传...");
                // 这里可以读取进度文件获取 startChunk
            }
            
            for (int i = startChunk; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, fileBytes.length);
                byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);
                
                Map<String, String> params = new HashMap<>();
                params.put("uploadId", uploadId);
                params.put("chunkIndex", String.valueOf(i));
                params.put("totalChunks", String.valueOf(totalChunks));
                
                System.out.print("上传分片 " + (i + 1) + "/" + totalChunks + " ... ");
                
                try {
                    FileUploadUtils.UploadResult result = FileUploadUtils.uploadBytes(
                        uploadUrl, chunk, fileName + ".part" + i, fieldName, params
                    );
                    
                    if (result.success) {
                        System.out.println("✅");
                        // 保存进度
                        Files.writeString(progressFile, 
                            "{\"uploadId\":\"" + uploadId + "\",\"lastChunk\":" + i + "}");
                    } else {
                        System.out.println("❌ 上传失败，可以稍后继续");
                        return "上传中断，可以使用上传ID继续: " + uploadId;
                    }
                } catch (IOException e) {
                    System.out.println("❌ 网络错误，可以稍后继续");
                    return "上传中断，可以使用上传ID继续: " + uploadId;
                }
            }
            
            // 清理进度文件
            Files.deleteIfExists(progressFile);
            
            return "✅ 文件上传完成！";
        }
    }

    // ============ 测试示例 ============
    public static void main(String[] args) {
        String uploadUrl = "http://localhost:8080/upload";
        byte[] testData = "测试数据 - 高级示例".repeat(100).getBytes(StandardCharsets.UTF_8);
        
        System.out.println("=============================================");
        System.out.println("        高级示例演示");
        System.out.println("=============================================\n");
        
        // 示例1: 带进度的上传
        System.out.println("【示例1】带进度监控的上传");
        System.out.println("─────────────────────────────────────────────");
        try {
            String result = ProgressUploader.uploadWithProgress(
                uploadUrl, testData, "progress-test.txt", "file",
                (bytesWritten, totalBytes) -> {
                    int percentage = (int) ((bytesWritten * 100) / totalBytes);
                    System.out.print("\r进度: " + percentage + "% [" + bytesWritten + "/" + totalBytes + " bytes]");
                }
            );
            System.out.println("\n✅ 完成\n");
        } catch (Exception e) {
            System.out.println("\n❌ 失败: " + e.getMessage() + "\n");
        }
        
        // 示例2: 带重试的上传
        System.out.println("【示例2】带重试机制的上传");
        System.out.println("─────────────────────────────────────────────");
        String result = RetryUploader.uploadWithRetry(
            uploadUrl, testData, "retry-test.txt", "file", 3
        );
        System.out.println(result + "\n");
        
        // 示例3: 大文件分片上传
        System.out.println("【示例3】大文件分片上传");
        System.out.println("─────────────────────────────────────────────");
        try {
            byte[] largeData = new byte[5 * 1024 * 1024]; // 5MB
            Arrays.fill(largeData, (byte) 'A');
            
            String result3 = ChunkedUploader.uploadLargeFile(
                uploadUrl, largeData, "large-file.bin", "file", 1024 * 1024 // 1MB chunks
            );
            System.out.println(result3 + "\n");
        } catch (Exception e) {
            System.out.println("❌ 失败: " + e.getMessage() + "\n");
        }
        
        System.out.println("=============================================");
        System.out.println("更多示例请查看源代码");
        System.out.println("=============================================");
    }
}
