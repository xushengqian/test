import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单的 HTTP 文件上传测试服务器
 * 使用 JDK 自带的 HttpServer，无需额外依赖
 * 
 * 运行此服务器后，可以测试其他示例代码的上传功能
 */
public class SimpleUploadServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册上传处理器
        server.createContext("/upload", new FileUploadHandler());
        
        // 注册首页
        server.createContext("/", new HomeHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("========================================");
        System.out.println("文件上传测试服务器已启动");
        System.out.println("端口: " + port);
        System.out.println("上传接口: http://localhost:" + port + "/upload");
        System.out.println("首页: http://localhost:" + port + "/");
        System.out.println("========================================");
        System.out.println("按 Ctrl+C 停止服务器");
    }

    /**
     * 文件上传处理器
     */
    static class FileUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                String response = "只支持 POST 请求";
                exchange.sendResponseHeaders(405, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            try {
                // 获取 Content-Type
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                System.out.println("\n收到上传请求:");
                System.out.println("Content-Type: " + contentType);
                
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    String boundary = extractBoundary(contentType);
                    Map<String, Object> parts = parseMultipartData(exchange.getRequestBody(), boundary);
                    
                    // 打印接收到的数据
                    System.out.println("\n接收到的表单数据:");
                    for (Map.Entry<String, Object> entry : parts.entrySet()) {
                        if (entry.getValue() instanceof FilePart) {
                            FilePart file = (FilePart) entry.getValue();
                            System.out.println("  文件字段: " + entry.getKey());
                            System.out.println("    文件名: " + file.fileName);
                            System.out.println("    大小: " + file.data.length + " bytes");
                            System.out.println("    Content-Type: " + file.contentType);
                            
                            // 如果是文本文件，显示内容预览
                            if (file.contentType.startsWith("text/")) {
                                String preview = new String(file.data, StandardCharsets.UTF_8);
                                if (preview.length() > 100) {
                                    preview = preview.substring(0, 100) + "...";
                                }
                                System.out.println("    内容预览: " + preview);
                            }
                        } else {
                            System.out.println("  参数: " + entry.getKey() + " = " + entry.getValue());
                        }
                    }
                    
                    // 构建响应
                    StringBuilder responseBuilder = new StringBuilder();
                    responseBuilder.append("文件上传成功!\n\n");
                    responseBuilder.append("接收到的数据:\n");
                    for (Map.Entry<String, Object> entry : parts.entrySet()) {
                        if (entry.getValue() instanceof FilePart) {
                            FilePart file = (FilePart) entry.getValue();
                            responseBuilder.append(String.format("- 文件: %s (字段名: %s, 大小: %d bytes)\n",
                                file.fileName, entry.getKey(), file.data.length));
                        } else {
                            responseBuilder.append(String.format("- 参数: %s = %s\n",
                                entry.getKey(), entry.getValue()));
                        }
                    }
                    
                    String response = responseBuilder.toString();
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                } else {
                    String response = "不支持的 Content-Type: " + contentType;
                    exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "上传处理失败: " + e.getMessage();
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }

        private String extractBoundary(String contentType) {
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("boundary=")) {
                    return part.substring("boundary=".length());
                }
            }
            return null;
        }

        private Map<String, Object> parseMultipartData(InputStream input, String boundary) throws IOException {
            Map<String, Object> parts = new HashMap<>();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 读取所有数据
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            byte[] data = baos.toByteArray();
            String dataStr = new String(data, StandardCharsets.ISO_8859_1);
            
            // 分割各个部分
            String delimiter = "--" + boundary;
            String[] segments = dataStr.split(delimiter);
            
            for (String segment : segments) {
                if (segment.trim().isEmpty() || segment.trim().equals("--")) {
                    continue;
                }
                
                // 查找头部和内容的分隔位置
                int headerEnd = segment.indexOf("\r\n\r\n");
                if (headerEnd == -1) {
                    headerEnd = segment.indexOf("\n\n");
                    if (headerEnd == -1) continue;
                }
                
                String headers = segment.substring(0, headerEnd);
                String content = segment.substring(headerEnd + 4); // 跳过 \r\n\r\n
                
                // 移除末尾的 \r\n
                if (content.endsWith("\r\n")) {
                    content = content.substring(0, content.length() - 2);
                }
                
                // 解析 Content-Disposition
                String name = null;
                String fileName = null;
                String contentType = "text/plain";
                
                String[] headerLines = headers.split("\r\n");
                for (String line : headerLines) {
                    if (line.toLowerCase().startsWith("content-disposition:")) {
                        // 提取 name 和 filename
                        if (line.contains("name=\"")) {
                            int nameStart = line.indexOf("name=\"") + 6;
                            int nameEnd = line.indexOf("\"", nameStart);
                            name = line.substring(nameStart, nameEnd);
                        }
                        if (line.contains("filename=\"")) {
                            int fileNameStart = line.indexOf("filename=\"") + 10;
                            int fileNameEnd = line.indexOf("\"", fileNameStart);
                            fileName = line.substring(fileNameStart, fileNameEnd);
                        }
                    } else if (line.toLowerCase().startsWith("content-type:")) {
                        contentType = line.substring(13).trim();
                    }
                }
                
                if (name != null) {
                    if (fileName != null) {
                        // 这是一个文件
                        byte[] fileData = content.getBytes(StandardCharsets.ISO_8859_1);
                        parts.put(name, new FilePart(fileName, fileData, contentType));
                    } else {
                        // 这是一个普通字段
                        parts.put(name, content);
                    }
                }
            }
            
            return parts;
        }
    }

    /**
     * 首页处理器
     */
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<!DOCTYPE html>\n" +
                    "<html lang=\"zh-CN\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <title>文件上传测试服务器</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }\n" +
                    "        h1 { color: #333; }\n" +
                    "        .form-group { margin: 20px 0; }\n" +
                    "        label { display: block; margin-bottom: 5px; font-weight: bold; }\n" +
                    "        input[type=\"file\"], input[type=\"text\"] { padding: 10px; width: 100%; box-sizing: border-box; }\n" +
                    "        button { padding: 10px 20px; background: #007bff; color: white; border: none; cursor: pointer; }\n" +
                    "        button:hover { background: #0056b3; }\n" +
                    "        #result { margin-top: 20px; padding: 15px; background: #f8f9fa; border: 1px solid #dee2e6; white-space: pre-wrap; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>📁 文件上传测试服务器</h1>\n" +
                    "    <p>使用此页面测试文件上传功能</p>\n" +
                    "    \n" +
                    "    <form id=\"uploadForm\">\n" +
                    "        <div class=\"form-group\">\n" +
                    "            <label for=\"userId\">用户ID:</label>\n" +
                    "            <input type=\"text\" id=\"userId\" name=\"userId\" value=\"user123\">\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div class=\"form-group\">\n" +
                    "            <label for=\"description\">描述:</label>\n" +
                    "            <input type=\"text\" id=\"description\" name=\"description\" value=\"测试上传\">\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div class=\"form-group\">\n" +
                    "            <label for=\"file\">选择文件:</label>\n" +
                    "            <input type=\"file\" id=\"file\" name=\"file\" required>\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <button type=\"submit\">上传文件</button>\n" +
                    "    </form>\n" +
                    "    \n" +
                    "    <div id=\"result\"></div>\n" +
                    "    \n" +
                    "    <script>\n" +
                    "        document.getElementById('uploadForm').addEventListener('submit', async (e) => {\n" +
                    "            e.preventDefault();\n" +
                    "            \n" +
                    "            const formData = new FormData();\n" +
                    "            formData.append('userId', document.getElementById('userId').value);\n" +
                    "            formData.append('description', document.getElementById('description').value);\n" +
                    "            formData.append('file', document.getElementById('file').files[0]);\n" +
                    "            \n" +
                    "            const resultDiv = document.getElementById('result');\n" +
                    "            resultDiv.textContent = '上传中...';\n" +
                    "            \n" +
                    "            try {\n" +
                    "                const response = await fetch('/upload', {\n" +
                    "                    method: 'POST',\n" +
                    "                    body: formData\n" +
                    "                });\n" +
                    "                \n" +
                    "                const text = await response.text();\n" +
                    "                resultDiv.textContent = '响应码: ' + response.status + '\\n\\n' + text;\n" +
                    "            } catch (error) {\n" +
                    "                resultDiv.textContent = '上传失败: ' + error.message;\n" +
                    "            }\n" +
                    "        });\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";

            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    /**
     * 文件部分数据封装
     */
    static class FilePart {
        String fileName;
        byte[] data;
        String contentType;

        FilePart(String fileName, byte[] data, String contentType) {
            this.fileName = fileName;
            this.data = data;
            this.contentType = contentType;
        }
    }
}
