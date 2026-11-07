<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ApiRequestError, fetchOverview, fetchProviders } from './api'
import type { AssetType, OverviewCard, OverviewResponse, Provider } from './types'
import {
  formatAge,
  formatClock,
  formatDateTime,
  formatValue,
  healthLabel,
  qualityLabel,
  qualityTone,
  sourceLabel,
  sourceTone,
} from './present'

const SITE_ID = '11111111-1111-4111-8111-111111111111'
const ENVIRONMENT = 'pilot'
const REFRESH_MS = 30_000

const overview = ref<OverviewResponse | null>(null)
const providers = ref<Provider[]>([])
const loading = ref(false)
const error = ref<{ code: string; message: string; requestId: string | null } | null>(null)
const lastLoadedAt = ref<Date | null>(null)
const autoRefresh = ref(true)
const rangeMinutes = ref(15)
const typeFilter = ref<AssetType | 'all'>('all')

let timer: number | undefined

const assetTypeLabel: Record<AssetType, string> = {
  host: '主机',
  container: '容器',
  service: '服务',
  service_instance: '服务实例',
  database: '数据库',
}

/** 卡片排序：异常优先，其次注意，最后未知与正常，避免异常被排到折叠区外 */
const healthRank: Record<OverviewCard['health'], number> = {
  critical: 0,
  warning: 1,
  unknown: 2,
  healthy: 3,
}

const visibleCards = computed(() => {
  const cards = overview.value?.cards ?? []
  return cards
    .filter((card) => typeFilter.value === 'all' || card.asset.type === typeFilter.value)
    .slice()
    .sort((a, b) => healthRank[a.health] - healthRank[b.health])
})

/** 统计未知数量，作为“不能假装正常”的显式提醒 */
const unknownCount = computed(
  () => (overview.value?.cards ?? []).filter((card) => card.health === 'unknown').length,
)

const degradedSources = computed(
  () => (overview.value?.sources ?? []).filter((source) => source.state !== 'ok'),
)

const providerName = computed(() => {
  const map = new Map<string, string>()
  for (const provider of providers.value) {
    map.set(provider.id, provider.name)
  }
  return map
})

function buildRange(): { start: string; end: string } {
  const end = new Date()
  const start = new Date(end.getTime() - rangeMinutes.value * 60_000)
  return { start: start.toISOString(), end: end.toISOString() }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const range = buildRange()
    const [overviewResponse, providerResponse] = await Promise.all([
      fetchOverview({ siteId: SITE_ID, environment: ENVIRONMENT, ...range }),
      fetchProviders(),
    ])
    overview.value = overviewResponse
    providers.value = providerResponse.items
    lastLoadedAt.value = new Date()
  } catch (err) {
    if (err instanceof ApiRequestError) {
      error.value = { code: err.code, message: err.message, requestId: err.requestId }
    } else {
      error.value = { code: 'NETWORK_ERROR', message: '无法连接融合服务', requestId: null }
    }
  } finally {
    loading.value = false
  }
}

function scheduleRefresh() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
  if (autoRefresh.value) {
    timer = window.setInterval(load, REFRESH_MS)
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  scheduleRefresh()
}

onMounted(async () => {
  await load()
  scheduleRefresh()
})

onUnmounted(() => {
  if (timer !== undefined) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="app">
    <header class="top">
      <h1>统一监控总览</h1>
      <span class="sub">站点 {{ SITE_ID }} · 环境 {{ ENVIRONMENT }}</span>
      <span class="sub" v-if="lastLoadedAt">加载于 {{ formatDateTime(lastLoadedAt.toISOString()) }}</span>
    </header>

    <div class="toolbar">
      <label for="range">时间范围</label>
      <select id="range" v-model.number="rangeMinutes" @change="load">
        <option :value="15">最近 15 分钟</option>
        <option :value="60">最近 1 小时</option>
        <option :value="360">最近 6 小时</option>
      </select>

      <label for="type">资产类型</label>
      <select id="type" v-model="typeFilter">
        <option value="all">全部</option>
        <option value="host">主机</option>
        <option value="database">数据库</option>
        <option value="service">服务</option>
        <option value="service_instance">服务实例</option>
        <option value="container">容器</option>
      </select>

      <button @click="load" :disabled="loading">
        <span v-if="loading" class="spin"></span>
        {{ loading ? '刷新中' : '立即刷新' }}
      </button>
      <button @click="toggleAutoRefresh">
        {{ autoRefresh ? '暂停自动刷新' : '开启自动刷新' }}
      </button>
    </div>

    <!-- 合成数据提示：来源能力尚未验证时必须显式告知 -->
    <div class="banner warn" v-if="providers.length">
      当前数据来自合成适配器：
      <template v-for="provider in providers" :key="provider.id">
        <span class="pill muted">{{ provider.name }}</span>
      </template>
      上游能力标记为 unverified，接入真实 HertzBeat / SkyWalking 前不得用于生产判断。
    </div>

    <div class="banner bad" v-if="error">
      请求失败 <code>{{ error.code }}</code>：{{ error.message }}
      <span v-if="error.requestId" class="sub">requestId {{ error.requestId }}</span>
    </div>

    <div class="banner warn" v-if="overview?.partial && !error">
      本次结果不完整：部分指标不支持或来源未映射，缺失项已标记为“不支持”，未用 0 替代。
    </div>

    <div class="banner warn" v-if="degradedSources.length">
      来源状态异常：
      <template v-for="source in degradedSources" :key="source.providerId">
        <span class="pill" :class="sourceTone[source.state]">
          {{ providerName.get(source.providerId) ?? source.providerId }} · {{ sourceLabel[source.state] }}
        </span>
      </template>
    </div>

    <section class="sources" v-if="overview">
      <div class="source-card" v-for="source in overview.sources" :key="source.providerId">
        <div class="title">
          <span>{{ providerName.get(source.providerId) ?? source.providerId }}</span>
          <span class="pill" :class="sourceTone[source.state]">{{ sourceLabel[source.state] }}</span>
        </div>
        <div class="meta">
          <span>采集时间 {{ formatClock(source.sourceCollectedAt) }}</span>
          <span>数据年龄 {{ formatAge(source.ageSeconds) }}</span>
        </div>
      </div>
    </section>

    <div class="error-box" v-if="!overview && loading">
      <span class="spin"></span> 正在加载总览…
    </div>

    <div class="empty" v-else-if="!overview && error">
      暂无数据，请先启动融合服务（默认 http://localhost:8080）。
    </div>

    <div class="empty" v-else-if="overview && visibleCards.length === 0">
      该筛选条件下没有资产。
    </div>

    <section class="cards" v-else-if="overview">
      <article
        class="card"
        :class="'health-' + card.health"
        v-for="card in visibleCards"
        :key="card.asset.id"
      >
        <div class="card-head">
          <div>
            <div class="name">{{ card.asset.name }}</div>
            <div class="type">
              {{ assetTypeLabel[card.asset.type] }} · {{ card.asset.environment }}
            </div>
          </div>
          <span
            class="pill"
            :class="
              card.health === 'healthy'
                ? 'ok'
                : card.health === 'warning'
                  ? 'warn'
                  : card.health === 'critical'
                    ? 'bad'
                    : 'muted'
            "
          >
            {{ healthLabel[card.health] }}
          </span>
        </div>

        <div class="signals">
          <div
            class="signal"
            :class="{ 'is-missing': signal.quality !== 'valid' }"
            v-for="signal in card.signals"
            :key="signal.metricId"
          >
            <span class="label">{{ signal.metricId }}</span>
            <span class="value">{{ formatValue(signal.value, signal.unit) }}</span>
            <span class="note">
              <span class="pill" :class="qualityTone[signal.quality]">
                {{ qualityLabel[signal.quality] }}
              </span>
              <template v-if="signal.sampleScope.mode === 'unknown'">
                · 采样范围未知
              </template>
              <template v-else-if="signal.sampleScope.mode === 'sampled'">
                · 采样 {{ signal.sampleScope.ratio !== null ? (signal.sampleScope.ratio * 100).toFixed(0) + '%' : '比例未知' }}
              </template>
              <template v-if="signal.quality === 'valid'">
                · {{ formatClock(signal.sourceCollectedAt) }}
              </template>
            </span>
          </div>
        </div>
      </article>
    </section>

    <div class="legend">
      <div>共 {{ overview?.cards.length ?? 0 }} 个资产，其中 {{ unknownCount }} 个因缺少有效样本显示为“未知”。</div>
      <div>“未知”表示没有可用样本，不等于正常；指标不支持时数值显示为“—”，不填 0。</div>
      <div>健康度仅依据 CPU、接口错误率与 P95 判定，其余指标只做展示，不参与评级。</div>
    </div>
  </div>
</template>
