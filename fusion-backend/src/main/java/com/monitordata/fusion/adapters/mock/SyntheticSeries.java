package com.monitordata.fusion.adapters.mock;

import com.monitordata.fusion.metrics.MetricPoint;
import com.monitordata.fusion.metrics.Quality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 确定性合成数据生成器，仅用于本地联调与契约测试。
 * 不使用随机漫步上的真实语义，页面必须标注为合成数据。
 */
final class SyntheticSeries {

    private SyntheticSeries() {
    }

    /**
     * 生成固定步长的合成序列。seed 由资产与指标派生，同一请求多次调用结果一致。
     */
    static List<MetricPoint> generate(long seed, Instant start, Instant end, int stepSeconds,
                                      double base, double amplitude, int missingEvery) {
        List<MetricPoint> points = new ArrayList<>();
        Random random = new Random(seed);
        long total = (end.getEpochSecond() - start.getEpochSecond() + stepSeconds - 1) / stepSeconds;
        for (long i = 0; i < total; i++) {
            Instant time = start.plusSeconds(i * stepSeconds);
            if (!time.isBefore(end)) {
                break;
            }
            if (missingEvery > 0 && i % missingEvery == missingEvery - 1) {
                // 采集缺口用 no_data 表达，绝不补 0
                points.add(new MetricPoint(time, null, Quality.NO_DATA));
                continue;
            }
            double wave = Math.sin(i / 6.0) * amplitude;
            double jitter = (random.nextDouble() - 0.5) * amplitude * 0.3;
            points.add(new MetricPoint(time, round(base + wave + jitter), Quality.VALID));
        }
        return points;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
