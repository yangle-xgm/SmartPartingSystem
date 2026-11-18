<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">PAYMENTS</span>
          <h1 class="h-hero__title">支付记录</h1>
          <p class="h-hero__desc">停车缴费与月卡充值的资金流水</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>¥{{ paidAmount }}</b>
          <span>累计支付</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ paidCount }}</b>
          <span>成功笔数</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ refundCount }}</b>
          <span>退款笔数</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="chips">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          class="chips__item"
          :class="{ active: active === f.value }"
          @click="active = f.value"
        >{{ f.label }}</button>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="loading">
          <div v-for="n in 3" :key="`sk-${n}`" class="h-sk h-sk--card" />
        </template>
        <template v-else-if="visible.length">
          <div v-for="p in visible" :key="p.payNo" class="h-card pay">
            <div class="pay__head">
              <div class="pay__amount">
                <span>¥</span><b>{{ money(p.amount) }}</b>
              </div>
              <span class="h-pill" :data-tone="statusTone(p.payStatus)">
                {{ statusText(p.payStatus) }}
              </span>
            </div>

            <div class="pay__grid">
              <div class="pay__cell">
                <span class="pay__k">支付方式</span>
                <b class="pay__v">{{ payTypeText(p.payType) }}</b>
              </div>
              <div class="pay__cell">
                <span class="pay__k">支付时间</span>
                <b class="pay__v">{{ shortTime(p.payTime || p.createTime) }}</b>
              </div>
              <div class="pay__cell">
                <span class="pay__k">关联订单</span>
                <b class="pay__v">{{ p.orderId ? `#${p.orderId}` : '月卡/其他' }}</b>
              </div>
              <div class="pay__cell">
                <span class="pay__k">交易号</span>
                <b class="pay__v ellipsis">{{ p.transactionId || '—' }}</b>
              </div>
            </div>

            <div class="pay__no">流水号 {{ p.payNo }}</div>
          </div>
        </template>
        <div v-else class="h-card h-empty">
          <van-icon name="balance-list-o" class="h-empty__icon" />
          <div class="h-empty__title">{{ emptyText }}</div>
          <div class="h-empty__hint">缴费成功后会在这里留下流水，可随时核对</div>
        </div>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPaymentList } from '@/api/payment'

const router = useRouter()

const filters = [
  { label: '全部', value: -1 },
  { label: '成功', value: 1 },
  { label: '待支付', value: 0 },
  { label: '退款', value: 3 }
]
const active = ref(-1)
const loading = ref(true)
const refreshing = ref(false)
const list = ref([])

const paid = computed(() => list.value.filter(p => p.payStatus === 1))
const paidCount = computed(() => paid.value.length)
const paidAmount = computed(() =>
  paid.value.reduce((s, p) => s + (Number(p.amount) || 0), 0).toFixed(2))
const refundCount = computed(() => list.value.filter(p => p.payStatus === 3).length)

const visible = computed(() => {
  if (active.value === -1) return list.value
  return list.value.filter(p => p.payStatus === active.value)
})

const emptyText = computed(() => {
  if (active.value === 1) return '还没有成功的支付记录'
  if (active.value === 0) return '没有待支付的流水'
  if (active.value === 3) return '没有退款记录'
  return '暂无支付记录'
})

const money = (v) => (v == null ? '0.00' : Number(v).toFixed(2))

function shortTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

function payTypeText(t) {
  if (t === 1) return '微信支付'
  if (t === 2) return '支付宝'
  if (t === 3) return '现金'
  return '—'
}

function statusText(s) {
  if (s === 1) return '支付成功'
  if (s === 2) return '支付失败'
  if (s === 3) return '已退款'
  return '待支付'
}

function statusTone(s) {
  if (s === 1) return 'teal'
  if (s === 3) return 'warning'
  if (s === 2) return 'danger'
  return 'muted'
}

function goBack() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/mine')
}

onMounted(loadData)

async function loadData() {
  refreshing.value = true
  try {
    // 后端 role=0 强制只返回本人流水，这里只传分页
    const res = await getPaymentList({ current: 1, size: 100 })
    list.value = res.code === 200 ? (res.data?.records || []) : []
  } catch {
    list.value = []
  } finally {
    refreshing.value = false
    loading.value = false
  }
}
</script>

<style scoped>
.chips {
  display: flex;
  gap: 6px;
  margin-bottom: var(--sp-2);
}
.chips__item {
  height: 28px;
  padding: 0 14px;
  font-size: 12px;
  color: var(--sp-text-2);
  background: var(--sp-card);
  border: 1px solid var(--sp-card-border);
  border-radius: var(--sp-radius-pill);
}
.chips__item.active {
  color: var(--sp-brand);
  font-weight: 700;
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}

.pay + .pay { margin-top: var(--sp-gap-card); }

.pay__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pay__amount { color: var(--sp-text); }
.pay__amount span { font-size: 13px; margin-right: 2px; }
.pay__amount b {
  font-family: var(--sp-font-num);
  font-size: 22px;
  font-weight: 700;
}

.pay__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 12px;
  margin-top: var(--sp-3);
  padding: 12px;
  background: var(--sp-surface-2);
  border-radius: var(--sp-radius-btn);
}
.pay__cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.pay__k { font-size: 11px; color: var(--sp-text-3); }
.pay__v { font-size: 13px; font-weight: 600; color: var(--sp-text); }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pay__no {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--sp-divider);
  font-size: 11px;
  color: var(--sp-text-3);
  word-break: break-all;
}
</style>
