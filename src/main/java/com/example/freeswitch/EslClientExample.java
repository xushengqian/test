package com.example.freeswitch;

import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class EslClientExample {

    private static final Logger logger = LoggerFactory.getLogger(EslClientExample.class);

    public static void main(String[] args) {
        Client client = new Client();

        try {
            // 连接到 FreeSWITCH ESL
            // 默认端口 8021，默认密码 ClueCon
            client.connect("localhost", 8021, "ClueCon", 10);
            logger.info("成功连接到 FreeSWITCH ESL");
            
            // 这里可以添加事件监听或发送命令
            // client.addEventListener(...);
            
        } catch (InboundConnectionFailure e) {
            logger.error("连接 FreeSWITCH 失败", e);
        }
    }
}
