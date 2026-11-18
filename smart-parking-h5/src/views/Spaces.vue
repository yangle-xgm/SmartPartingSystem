<template>
  <div class="h-page spaces">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">MY PARKING SPACES</span>
          <h1 class="h-hero__title">我的车位</h1>
          <p class="h-hero__desc">闲置时段开放共享，临停收益按 7:3 分账</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ spaces.length }}</b>
          <span>固定车位</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ sharedCount }}</b>
          <span>共享中</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ money(income.total) }}</b>
          <span>累计收益(元)</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-section">
        <span class="h-section__title">车位列表</span>
        <span class="h-section__more" @click="loadAll">刷新</span>
      </div>

      <template v-if="loading">
        <div v-for="n in 2" :key="`sk-${n}`" class="h-sk h-sk--card" />
      </template>
      <template v-else-if="spaces.length">
        <div v-for="s in spaces" :key="s.id" class="h-card space">
          <div class="space__head">
            <div class="space__no">
              <b>{{ s.spaceNo }}</b>
              <span>{{ s.area }} 区 · {{ s.floor || 'B1' }}</span>
            </div>
            <span class="h-pill" :data-tone="statusTone(s.status)">{{ statusText(s.status) }}</span>
          </div>

          <div class="h-kv">
            <span class="h-kv__k">月租参考价</span>
            <span class="h-kv__v">{{ s.monthPrice ? `¥${money(s.monthPrice)} / 月` : '未定价' }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">共享收益</span>
            <span class="h-kv__v" :class="{ on: s.isShared === 1 }">
              {{ s.isShared === 1 ? '已开启，临停按 70% 分给你' : '未开启' }}
            </span>
          </div>

          <div class="space__share">
            <div class="space__share-txt">
              <b>开放共享</b>
              <span>{{ s.isShared === 1 ? '他人临停时自动计费并分润' : '开启后闲置时段可为他人提供临停' }}</span>
            </div>
            <van-switch
              :model-value="s.isShared === 1"
              size="22px"
              :loading="toggling === s.id"
              @update:model-value="(val) => onToggleShare(s, val)"
            />
          </div>
        </div>
      </template>
      <div v-else class="h-card h-empty">
        <van-icon name="park" class="h-empty__icon" />
        <div class="h-empty__title">名下暂无固定车位</div>
        <div class="h-empty__hint">购买或租赁固定车位后，可在此开启共享赚取收益</div>
      </div>

      <div class="h-section">
        <span class="h-section__title">共享收益</span>
        <span class="h-section__more">{{ income.count || 0 }} 笔分润</span>
      </div>

      <div class="h-card income">
        <div class="income__sum">
          <span>累计到手</span>
          <b><i>¥</i>{{ money(income.total) }}</b>
        </div>
        <div class="income__sub">
          这些订单共产生停车费 ¥{{ money(income.grossTotal) }}，物业留存 30%
        </div>
      </div>

      <template v-if="income.records && income.records.length">
        <div v-for="r in income.records" :key="r.id" class="h-card h-card--tap rec">
          <div class="rec__main">
            <span class="rec__title">车位 #{{ r.spaceId }} 共享分润</span>
            <span class="rec__time">{{ dateOnly(r.createTime) }}</span>
          </div>
          <div class="rec__amount">
            <b>+{{ money(r.ownerAmount) }}</b>
            <span>总费用 {{ money(r.totalAmount) }}</span>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="h-card h-empty">
        <van-icon name="gold-coin-o" class="h-empty__icon" />
        <div class="h-empty__title">还没有共享收益</div>
        <div class="h-empty__hint">开启车位共享后，他人临停产生的费用会自动分润给你</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { toastOnce } from '@/utils/request'
import { showSuccessToast } from 'vant'
import { getMySpaces, toggleSpaceShare } from '@/api/parking'
import { getMyShareIncome } from '@/api/payment'

const router = useRouter()

const spaces = ref([])
const loading = ref(true)
const toggling = ref(null)
const income = ref({ total: 0, grossTotal: 0, count: 0, records: [] })

const sharedCount = computed(() => spaces.value.filter((s) => s.isShared === 1).length)

const STATUS = {
  0: { text: '空闲', tone: 'teal' },
  1: { text: '占用中', tone: 'muted' },
  2: { text: '已预约', tone: 'warning' },
  3: { text: '维护中', tone: 'danger' }
}

onMounted(loadAll)

async function loadAll() {
  try {
    const [sp, inc] = await Promise.allSettled([getMySpaces(), getMyShareIncome()])
    if (sp.status === 'fulfilled' && sp.value.code === 200) {
      spaces.value = Array.isArray(sp.value.data) ? sp.value.data : []
    } else if (sp.status === 'rejected') {
      toastOnce(sp.reason, '车位加载失败')
    }
    if (inc.status === 'fulfilled' && inc.value.code === 200) {
      income.value = inc.value.data || { total: 0, grossTotal: 0, count: 0, records: [] }
    }
  } finally {
    loading.value = false
  }
}

async function onToggleShare(space, val) {
  const next = val ? 1 : 0
  toggling.value = space.id
  try {
    const res = await toggleSpaceShare(space.id, next)
    if (res.code === 200) {
      space.isShared = next
      showSuccessToast(next ? '已开启共享' : '已关闭共享')
    }
  } catch (e) {
    toastOnce(e, '操作失败')
  } finally {
    toggling.value = null
  }
}

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/mine')
}

function statusText(s) {
  return STATUS[s]?.text || '未知'
}
function statusTone(s) {
  return STATUS[s]?.tone || 'muted'
}
function dateOnly(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}
function money(v) {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}
</script>

<style scoped>
.spaces { padding-bottom: calc(var(--sp-4) + env(safe-area-inset-bottom)); }

.space__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--sp-2);
}
.space__no {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.space__no b {
  font-size: 19px;
  font-family: var(--sp-font-num);
  letter-spacing: 0.5px;
}
.space__no span { font-size: 12px; color: var(--sp-text-3); margin-top: 2px; }

.h-kv__v.on { color: var(--sp-teal); font-weight: 600; }

.space__share {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--sp-divider);
}
.space__share-txt {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.space__share-txt b { font-size: 13px; }
.space__share-txt span { font-size: 11px; color: var(--sp-text-3); margin-top: 2px; }

.income { background: var(--sp-grad-gold); color: #2A2214; }
.income__sum {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.income__sum span { font-size: 12px; opacity: 0.85; }
.income__sum b { font-size: 26px; font-family: var(--sp-font-num); }
.income__sum i { font-style: normal; font-size: 14px; margin-right: 2px; }
.income__sub { margin-top: 6px; font-size: 11px; opacity: 0.8; }

.rec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px var(--sp-4);
}
.rec__main { display: flex; flex-direction: column; line-height: 1.4; }
.rec__title { font-size: 13px; font-weight: 600; }
.rec__time { font-size: 11px; color: var(--sp-text-3); margin-top: 2px; }
.rec__amount { text-align: right; display: flex; flex-direction: column; line-height: 1.4; }
.rec__amount b { font-size: 15px; color: var(--sp-teal); font-family: var(--sp-font-num); }
.rec__amount span { font-size: 11px; color: var(--sp-text-3); }
</style>
