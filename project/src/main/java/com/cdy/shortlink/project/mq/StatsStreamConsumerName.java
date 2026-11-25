package com.cdy.shortlink.project.mq;

import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 统计 Stream 消费者名称。
 * <p>
 * 每个 JVM 实例必须是独立的消费者名，否则多实例部署时 pending 消息会混在同一个名字下，
 * 无法判断是哪台实例没处理完。实例重启后由恢复任务通过 XCLAIM 接管旧实例遗留的 pending 消息。
 */
@Component
public class StatsStreamConsumerName {

    private final String name;

    public StatsStreamConsumerName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown";
        }
        this.name = "stats-consumer-" + host + "-" + ProcessHandle.current().pid();
    }

    public String getName() {
        return name;
    }
}
