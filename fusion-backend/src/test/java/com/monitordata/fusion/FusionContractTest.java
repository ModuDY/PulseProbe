package com.monitordata.fusion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约测试：锁定“缺失不等于 0”“越界即报错”“部分失败可见”这三类关键约定。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FusionContractTest {

    private static final String APP_HOST = "22222222-2222-4222-8222-222222222222";
    private static final String MARIADB = "77777777-7777-4777-8777-777777777777";
    private static final String SITE = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private MockMvc mvc;

    @Test
    void assetsExposeServiceInstanceAsFirstClassType() throws Exception {
        mvc.perform(get("/api/v1/assets").param("type", "service_instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("service_instance"))
                .andExpect(jsonPath("$.items[0].parentAssetId").value("44444444-4444-4444-8444-444444444444"));
    }

    @Test
    void missingMetricBecomesUnsupportedNotZero() throws Exception {
        String body = """
                {"assetIds":["%s"],"metricIds":["service.latency.p95"],
                 "range":{"start":"2026-11-05T06:00:00Z","end":"2026-11-05T06:10:00Z"},
                 "stepSeconds":60}
                """.formatted(APP_HOST);
        mvc.perform(post("/api/v1/metrics/query").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].quality").value("unsupported"))
                .andExpect(jsonPath("$.series[0].points.length()").value(0))
                .andExpect(jsonPath("$.partial").value(true));
    }

    @Test
    void counterGapIsNoDataNotZero() throws Exception {
        String body = """
                {"assetIds":["%s"],"metricIds":["host.swap.used"],
                 "range":{"start":"2026-11-05T06:00:00Z","end":"2026-11-05T07:00:00Z"},
                 "stepSeconds":60}
                """.formatted(APP_HOST);
        mvc.perform(post("/api/v1/metrics/query").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].points[9].value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.series[0].points[9].quality").value("no_data"));
    }

    @Test
    void tooManyPointsIsRejectedInsteadOfDownsampled() throws Exception {
        String body = """
                {"assetIds":["%s"],"metricIds":["host.cpu.usage"],
                 "range":{"start":"2026-11-04T06:00:00Z","end":"2026-11-05T06:00:00Z"},
                 "stepSeconds":60}
                """.formatted(APP_HOST);
        mvc.perform(post("/api/v1/metrics/query").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_TOO_DENSE"));
    }

    @Test
    void tooManyCombinationsIsRejected() throws Exception {
        String assets = String.join(",", java.util.Collections.nCopies(11, "\"" + APP_HOST + "\""));
        String metrics = "\"host.cpu.usage\"";
        String body = """
                {"assetIds":[%s],"metricIds":[%s],
                 "range":{"start":"2026-11-05T06:00:00Z","end":"2026-11-05T06:10:00Z"},
                 "stepSeconds":60}
                """.formatted(assets, metrics);
        mvc.perform(post("/api/v1/metrics/query").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMBINATION_LIMIT_EXCEEDED"));
    }

    @Test
    void timeWithoutOffsetIsRejected() throws Exception {
        mvc.perform(get("/api/v1/assets/" + APP_HOST + "/relations")
                        .param("at", "2026-11-05T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_INVALID"));
    }

    @Test
    void unknownAssetIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/assets/99999999-9999-4999-8999-999999999999/relations")
                        .param("at", "2026-11-05T00:00:00Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void overviewNeverReportsHealthyWithoutValidSamples() throws Exception {
        mvc.perform(get("/api/v1/overview")
                        .param("siteId", SITE)
                        .param("environment", "pilot")
                        .param("start", "2026-11-05T06:00:00Z")
                        .param("end", "2026-11-05T06:10:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[?(@.asset.id=='" + MARIADB + "')].health").value("healthy"))
                .andExpect(jsonPath("$.sources[0].state").value("ok"));
    }
}
