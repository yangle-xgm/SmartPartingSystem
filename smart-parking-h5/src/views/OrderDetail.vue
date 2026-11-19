<template>
  <div class="h-page detail">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">ORDER DETAIL</span>
          <h1 class="h-hero__title">订单详情</h1>
          <p class="h-hero__desc">{{ order.orderNo || '加载中…' }}</p>
        </div>
      </div>
      <div v-if="order.orderNo" class="h-hero__stats">
        <div class="h-hero__stat">
          <b>¥{{ money(order.amount) }}</b>
          <span>应付金额</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <b>{{ order.duration != null ? order.duration + '′' : '—' }}</b>
          <span>停车时长</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <b>{{ statusText }}</b>
          <span>当前状态</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <template v-if="loading">
        <div v-for="n in 3" :key="`sk-${n}`" class="h-sk h-sk--card" />
      </template>

      <template v-else-if="!order.orderNo">
        <div class="h-card h-empty">
          <van-icon name="warning-o" class="h-empty__icon" />
          <div class="h-empty__title">{{ errorText || '订单不存在' }}</div>
          <div class="h-empty__hint">可能已被取消，或不是本人车辆的订单</div>
          <button class="h-empty__cta" type="button" @click="router.replace('/orders')">返回订单列表</button>
        </div>
      </template>

      <template v-else>
        <div class="h-card">
          <div class="h-card__head">
            <span class="h-card__title">车辆与车位</span>
            <span class="h-pill" :data-tone="statusTone">{{ statusText }}</span>
          </div>
          <div class="plate-row">
            <span class="h-plate" :data-color="order.plateColor || '蓝'">{{ displayPlate(order) }}</span>
            <span class="plate-row__space">{{ order.spaceNo || '未分配车位' }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">入场时间</span>
            <span class="h-kv__v">{{ fullTime(order.entryTime) }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">出场时间</span>
            <span class="h-kv__v">{{ order.exitTime ? fullTime(order.exitTime) : '仍在场' }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">停车时长</span>
            <span class="h-kv__v">{{ order.duration != null ? order.duration + ' 分钟' : '—' }}</span>
          </div>
        </div>

        <div class="h-card">
          <div class="h-card__head">
            <span class="h-card__title">费用明细</span>
            <span class="h-card__extra">{{ rule ? rule.ruleName : '按临停标准计费' }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">应付金额</span>
            <span class="h-kv__v amount">¥{{ money(order.amount) }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">支付状态</span>
            <span class="h-kv__v">{{ payText }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">支付时间</span>
            <span class="h-kv__v">{{ order.payTime ? fullTime(order.payTime) : '—' }}</span>
          </div>
          <div v-if="rule" class="rule">
            <span class="rule__line">免费时长 {{ rule.freeMinutes ?? 0 }} 分钟</span>
            <span class="rule__line">
              {{ rule.unitDuration ? `${rule.unitDuration} 分钟` : '每计费单位' }}
              ¥{{ money(rule.unitPrice) }}
            </span>
            <span v-if="Number(rule.dailyCap) > 0" class="rule__line">24 小时封顶 ¥{{ money(rule.dailyCap) }}</span>
          </div>
          <p class="rule__hint">
            {{ order.status === 0
              ? '车辆仍在场内，最终金额以出场结算为准'
              : '已按出场时段的计费规则结算，金额不再变化' }}
          </p>
        </div>

        <div class="h-card">
          <div class="h-card__head">
            <span class="h-card__title">订单信息</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">订单号</span>
            <span class="h-kv__v mono">{{ order.orderNo }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">创建时间</span>
            <span class="h-kv__v">{{ fullTime(order.createTime) }}</span>
          </div>
          <div class="h-kv">
            <span class="h-kv__k">车辆编号</span>
            <span class="h-kv__v">{{ order.vehicleId || '—' }}</span>
          </div>
        </div>

        <div v-if="canCancel || canPay" class="actions">
          <button v-if="canCancel" class="h-btn h-btn--ghost" type="button" @click="handleCancel">
            取消订单
          </button>
          <button v-if="canPay" class="h-btn" type="button" @click="payVisible = true">
            去支付 ¥{{ money(order.amount) }}
          </button>
        </div>
      </template>
    </div>

    <van-action-sheet
      v-model:show="payVisible"
      :title="`支付 ¥${money(order.amount)}`"
      :actions="payActions"
      cancel-text="取消"
      close-on-click-action
      @select="onPaySelect"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { toastOnce } from '@/utils/request'
import { getOrderDetail, getBillingRules, cancelOrder, createPayment, wechatPay, alipayPay } from '@/api/order'
import { getUserId } from '@/api/user'
import { displayPlate } from '@/utils/plate'

const route = useRoute()
const router = useRouter()
const userId = getUserId()

const order = ref({})
const rule = ref(null)
const loading = ref(true)
const errorText = ref('')
const payVisible = ref(false)
/** 支付中锁：避免连点产生多笔流水 */
const paying = ref(false)

const payActions = [
  { name: '微信支付（模拟）', subname: '演示环境，确认即支付成功', value: 1 },
  { name: '支付宝（模拟）', subname: '演示环境，确认即支付成功', value: 2 }
]

const money = (v) => (v == null ? '0.00' : Number(v).toFixed(2))
const isFree = computed(() => order.value.status === 1 && order.value.payStatus === 0
  && Number(order.value.amount || 0) <= 0)
const canCancel = computed(() => order.value.status === 0)
const canPay = computed(() => order.value.status === 1 && order.value.payStatus === 0
  && Number(order.value.amount || 0) > 0)

const statusText = computed(() => {
  const o = order.value
  if (!o.orderNo) return '—'
  if (o.status === 0) return '停车中'
  if (o.status === 2) return '已取消'
  if (isFree.value) return '无需支付'
  if (o.payStatus === 1) return '已完成'
  if (o.payStatus === 2) return '已退款'
  return '待缴费'
})

const statusTone = computed(() => {
  const o = order.value
  if (o.status === 0) return 'teal'
  if (o.status === 2) return 'muted'
  if (isFree.value || o.payStatus === 1) return 'muted'
  return 'danger'
})

const payText = computed(() => {
  const s = order.value.payStatus
  if (s === 1) return '已支付'
  if (s === 2) return '已退款'
  if (isFree.value) return '免费时长内出场'
  return '未支付'
})

function fullTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function goBack() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/orders')
}

onMounted(load)

async function load() {
  const orderNo = route.params.orderNo
  if (!orderNo) {
    errorText.value = '缺少订单号'
    loading.value = false
    return
  }
  try {
    const [detail, rules] = await Promise.allSettled([getOrderDetail(orderNo), getBillingRules()])
    if (detail.status === 'fulfilled' && detail.value.code === 200 && detail.value.data) {
      order.value = detail.value.data
      if (rules.status === 'fulfilled' && rules.value.code === 200) {
        const list = Array.isArray(rules.value.data) ? rules.value.data : []
        rule.value = list.find(r => String(r.id) === String(order.value.billingRuleId)) || null
      }
    } else {
      errorText.value = detail.status === 'fulfilled' ? (detail.value.message || '订单不存在') : '订单加载失败'
    }
  } catch (e) {
    errorText.value = e.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  try {
    const res = await cancelOrder(order.value.orderNo)
    if (res.code === 200) {
      showSuccessToast('订单已取消')
      load()
    }
  } catch (e) {
    toastOnce(e, '取消失败')
  }
}

async function onPaySelect(action) {
  // 防重：双击/连点会生成多笔流水并各自置成功（报告 P2-10），与 Monthly.vue 保持一致
  if (paying.value) return
  paying.value = true
  payVisible.value = false
  try {
    const createRes = await createPayment(order.value.id, userId, order.value.amount, action.value)
    if (createRes.code !== 200) return
    const payNo = createRes.data?.payNo
    if (!payNo) {
      showToast('支付流水号生成失败')
      return
    }
    const payRes = action.value === 1 ? await wechatPay(payNo) : await alipayPay(payNo)
    if (payRes.code === 200) {
      showSuccessToast(payRes.data?.message || '支付成功')
      load()
    } else {
      toastOnce(payRes, '支付失败')
    }
  } catch (e) {
    toastOnce(e, '支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.detail { padding-bottom: 24px; }

.plate-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-2);
}
.plate-row__space {
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-text-2);
}

.amount {
  font-family: var(--sp-font-num);
  font-size: 16px;
  font-weight: 700;
  color: var(--sp-danger);
}
.mono { font-size: 12px; word-break: break-all; }

.rule {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.rule__line {
  padding: 4px 10px;
  font-size: 11px;
  color: var(--sp-text-2);
  background: var(--sp-surface-2);
  border-radius: var(--sp-radius-pill);
}
.rule__hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--sp-text-3);
}

.actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}
.actions .h-btn { flex: 1; height: 44px; }
</style>
