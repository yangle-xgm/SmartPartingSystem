package com.smartparking.stats.task;

import com.smartparking.stats.service.ParkingStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 车位总数校准任务。
 *
 * <p>{@code parking_stats.total_spaces} 原先只在 ENTRY / EXIT 事件里回填，
 * 于是「新增/删除车位 + 当天无车进出」就会留下永久漂移（实测 89 而实际 87，
 * 直接表现为看板占用率分母偏大）。读取侧已改为今天优先取实时值，
 * 这里再定时把落库值也纠正，保证 SQL 直查与历史图表一致。
 *
 * <p>默认 5 分钟一次；车位增删是低频操作，频率调高没有收益，只会多打几次内部接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpaceCountRefreshTask {

    private final ParkingStatsService parkingStatsService;

    @Scheduled(fixedDelayString = "${smart-parking.stats.space-count-refresh-ms:300000}",
            initialDelayString = "${smart-parking.stats.space-count-refresh-ms:300000}")
    public void refresh() {
        try {
            parkingStatsService.refreshTodaySpaceCounts();
        } catch (Exception e) {
            // 统计校准失败不影响任何业务链路，只记日志等下一轮
            log.warn("车位总数校准失败：{}", e.getMessage());
        }
    }
}
