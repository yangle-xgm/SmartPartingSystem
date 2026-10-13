package com.smartparking.common.core.monitor;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 bands.yaml 加载监控带配置。
 *
 * <p>使用 Spring Boot 已传递依赖的 SnakeYAML 解析（compile 作用域，无需额外引入），
 * 避免 Hutool 5.8+ 已移除的 {@code cn.hutool.extra.yaml} 包。</p>
 */
public final class BandsLoader {

    private BandsLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<Band> load(InputStream in) {
        Yaml yaml = new Yaml();
        List<Map<String, Object>> raw = yaml.load(in);
        List<Band> bands = new ArrayList<>();
        if (raw == null) {
            return bands;
        }
        for (Map<String, Object> m : raw) {
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
