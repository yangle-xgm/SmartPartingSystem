package com.smartparking.common.core.monitor;

import cn.hutool.extra.yaml.YamlUtil;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 bands.yaml 加载监控带配置（使用 Hutool YamlUtil 解析，避免引入额外依赖）。
 */
public final class BandsLoader {

    private BandsLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<Band> load(InputStream in) {
        List<?> raw = YamlUtil.load(in, List.class);
        List<Band> bands = new ArrayList<>();
        if (raw == null) {
            return bands;
        }
        for (Object o : raw) {
            Map<String, Object> m = (Map<String, Object>) o;
            Band band = new Band();
            band.setMetric(String.valueOf(m.get("metric")));
            band.setService(m.get("service") == null ? null : String.valueOf(m.get("service")));
            List<?> range = (List<?>) m.get("band");
            band.setBand(List.of(
                    new BigDecimal(range.get(0).toString()),
                    new BigDecimal(range.get(1).toString())));
            Map<String, Object> ob = (Map<String, Object>) m.get("on_breach");
            Band.OnBreach onBreach = new Band.OnBreach();
            onBreach.setSeverity(String.valueOf(ob.get("severity")));
            onBreach.setAction(String.valueOf(ob.get("action")));
            onBreach.setWriteIntent(Boolean.parseBoolean(String.valueOf(ob.get("write_intent"))));
            band.setOnBreach(onBreach);
            bands.add(band);
        }
        return bands;
    }
}
