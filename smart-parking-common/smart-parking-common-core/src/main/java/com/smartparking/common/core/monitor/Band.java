package com.smartparking.common.core.monitor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 监控指标带（阈值区间）配置，对应 bands.yaml 中的单条规则。
 */
public class Band {

    private String metric;
    private String service;
    private List<BigDecimal> band; // [min, max]
    private OnBreach onBreach;

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public List<BigDecimal> getBand() { return band; }
    public void setBand(List<BigDecimal> band) { this.band = band; }

    public OnBreach getOnBreach() { return onBreach; }
    public void setOnBreach(OnBreach onBreach) { this.onBreach = onBreach; }

    /** 越界时的处置配置（对应 on_breach 节点） */
    public static class OnBreach {
        private String severity;
        private String action;
        private boolean writeIntent;

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public boolean isWriteIntent() { return writeIntent; }
        public void setWriteIntent(boolean writeIntent) { this.writeIntent = writeIntent; }
    }
}
