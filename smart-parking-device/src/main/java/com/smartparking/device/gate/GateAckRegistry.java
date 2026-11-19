package com.smartparking.device.gate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 道闸指令回执登记表 + 指令流水。
 *
 * <p>两件事：
 * <ol>
 *   <li>按 seq 等待设备 ACK（阻塞等待带超时）——把「发指令」变成可确认的请求/响应；</li>
 *   <li>保留最近 N 条指令流水，供管理端页面/接口查看（沙箱演示的直观证据）。</li>
 * </ol>
 * 流水在内存里，重启即清空；这是演示与排障用的运行态数据，不落库。
 */
@Slf4j
@Component
public class GateAckRegistry {

    private static final int MAX_LOG = 200;

    private final Map<String, CompletableFuture<Map<String, String>>> waiting = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> commandLog = new ConcurrentLinkedDeque<>();

    /** 声明即将等待某个 seq 的回执（必须在发布指令前调用，避免 ACK 先到的竞态） */
    public void expect(String seq) {
        waiting.put(seq, new CompletableFuture<>());
    }

    /** 设备侧回执 */
    public void complete(String seq, Map<String, String> ack) {
        CompletableFuture<Map<String, String>> future = waiting.get(seq);
        if (future != null) {
            future.complete(ack);
        }
    }

    /** 等待回执，超时返回 null */
    public Map<String, String> await(String seq, long timeoutMs) {
        CompletableFuture<Map<String, String>> future = waiting.get(seq);
        if (future == null) {
            return null;
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("等待道闸回执异常 seq={}：{}", seq, e.getMessage());
            return null;
        } finally {
            waiting.remove(seq);
        }
    }

    /** 记录一条指令流水（最多保留 MAX_LOG 条） */
    public void record(Map<String, Object> entry) {
        commandLog.addFirst(entry);
        while (commandLog.size() > MAX_LOG) {
            commandLog.pollLast();
        }
    }

    /** 最近指令，新的在前 */
    public List<Map<String, Object>> recent(int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LOG));
        List<Map<String, Object>> list = new ArrayList<>(n);
        for (Map<String, Object> entry : commandLog) {
            if (list.size() >= n) {
                break;
            }
            list.add(entry);
        }
        return list;
    }
}
