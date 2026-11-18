<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <div>
          <span class="h-hero__eyebrow">PARKING ORDERS</span>
          <h1 class="h-hero__title">停车订单</h1>
          <p class="h-hero__desc">出入场自动生成订单，离场后在线结算</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ allOrders.length }}</b>
          <span>全部订单</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ ongoing.length }}</b>
          <span>停车中</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>¥{{ pendingAmount }}</b>
          <span>待缴金额</span>
        </div>
      </div>
    </header>

    <van-tabs v-model:active="activeTab" sticky offset-top="0" swipeable>
      <van-tab v-for="(t, i) in tabs" :key="i" :title="t.title" />
    </van-tabs>

    <div class="h-body">
      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="loading">
          <div v-for="n in 3" :key="`sk-${n}`" class="h-sk h-sk--card" />
        </template>
        <template v-else-if="filtered.length">
          <div
            v-for="o in filtered"
            :key="o.orderNo"
            class="h-card h-card--tap order"
            @click="openDetail(o)"
          >
            <div class="order__head">
              <span class="h-plate" :data-color="o.plateColor || '蓝'">
                {{ displayPlate(o) }}
              </span>
              <span class="h-pill" :data-tone="statusTone(o)">{{ statusText(o) }}</span>
            </div>

            <div class="order__grid">
              <div class="order__cell">
                <span class="order__k">车位</span>
                <b class="order__v">{{ o.spaceNo || '-' }}</b>
              </div>
              <div class="order__cell">
                <span class="order__k">停车时长</span>
                <b class="order__v">{{ o.status === 0 ? '进行中' : (o.duration != null ? o.duration + ' 分钟' : '—') }}</b>
              </div>
              <div class="order__cell">
                <span class="order__k">入场</span>
                <b class="order__v">{{ shortTime(o.entryTime) }}</b>
              </div>
              <div class="order__cell">
                <span class="order__k">出场</span>
                <b class="order__v">{{ shortTime(o.exitTime) }}</b>
              </div>
            </div>

            <div class="order__foot">
              <div class="order__amount">
                <span>¥</span><b>{{ money(o.amount) }}</b>
              </div>
              <div class="order__btns">
                <button
                  v-if="o.status === 0"
                  class="minibtn"
                  type="button"
                  @click.stop="handleCancel(o)"
                >取消订单</button>
                <span v-else-if="isFree(o)" class="paid">无需支付</span>
                <button
                  v-else-if="o.status === 1 && o.payStatus === 0"
                  class="minibtn minibtn--primary"
                  type="button"
                  @click.stop="handlePay(o)"
                >去支付</button>
                <span v-if="o.status === 1 && o.payStatus === 1" class="paid">已支付</span>
              </div>
            </div>

            <div class="order__no">订单号 {{ o.orderNo }}</div>
          </div>
        </template>
        <div v-else class="h-card h-empty">
          <van-icon name="orders-o" class="h-empty__icon" />
          {{ emptyText }}
        </div>
      </van-pull-refresh>
    </div>

    <!-- 支付方式 -->
    <van-action-sheet
      v-model:show="payVisible"
      :title="`支付 ¥${payAmount}`"
      :actions="payActions"
      cancel-text="取消"
      close-on-click-action
      @select="onPaySelect"
    />

    <TabBar :order-badge="pending.length" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { toastOnce } from '@/utils/request'
import { showToast, showSuccessToast } from 'vant'
import { getOrderList, cancelOrder, createPayment, wechatPay, alipayPay } from '@/api/order'
import { getUserId } from '@/api/user'
import { displayPlate } from '@/utils/plate'
import TabBar from '@/components/TabBar.vue'

const userId = getUserId()
const router = useRouter()

/** 进详情页看计费明细与出场时间 */
function openDetail(o) {
  if (!o?.orderNo) return
  router.push(`/orders/${o.orderNo}`)
}

const tabs = [
  { title: '全部' },
  { title: '进行中' },
  { title: '待缴费' },
  { title: '已完成' }
]
const activeTab = ref(0)
const refreshing = ref(false)
/** 首屏加载态：与下拉刷新分开，避免「先闪空态再跳列表」 */
const loading = ref(true)
const allOrders = ref([])

const payVisible = ref(false)
const payAmount = ref('0.00')
const payingOrder = ref(null)
const payActions = [
  { name: '微信支付（模拟）', subname: '演示环境，确认即支付成功', value: 1 },
  { name: '支付宝（模拟）', subname: '演示环境，确认即支付成功', value: 2 }
]

const ongoing = computed(() => allOrders.value.filter(o => o.status === 0))
/** 免费时长内出场：已结算 + 0 元 + 未支付 → 不需要去支付（E2E 报告 P2-10） */
const isFree = (o) => o.status === 1 && o.payStatus === 0 && Number(o.amount || 0) <= 0
const pending = computed(() => allOrders.value.filter(o => o.status === 1 && o.payStatus === 0 && !isFree(o)))
const pendingAmount = computed(() =>
  pending.value.reduce((s, o) => s + (Number(o.amount) || 0), 0).toFixed(2))

const filtered = computed(() => {
  const i = activeTab.value
  if (i === 1) return ongoing.value
  if (i === 2) return pending.value
  if (i === 3) return allOrders.value.filter(o => o.status === 1 && (o.payStatus === 1 || isFree(o)))
  return allOrders.value
})

const emptyText = computed(() => {
  const i = activeTab.value
  if (i === 1) return '当前没有停车中的订单'
  if (i === 2) return '没有待缴费的订单'
  if (i === 3) return '还没有已完成的订单'
  return '暂无停车订单'
})

const money = (v) => (v == null ? '0.00' : Number(v).toFixed(2))

function shortTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(5, 16)
}

function statusText(o) {
  if (o.status === 0) return '停车中'
  if (o.status === 2) return '已取消'
  if (isFree(o)) return '无需支付'
  if (o.payStatus === 1) return '已完成'
  if (o.payStatus === 3) return '已关闭'
  return '待缴费'
}

function statusTone(o) {
  if (o.status === 0) return 'teal'
  if (o.status === 2) return 'muted'
  if (isFree(o) || o.payStatus === 1) return 'muted'
  return 'danger'
}

onMounted(() => loadData())

async function loadData() {
  refreshing.value = true
  try {
    // 后端按 role=0 强制只返回本人车辆订单，前端仅需分页参数
    const res = await getOrderList({ current: 1, size: 100 })
    allOrders.value = res.code === 200 ? (res.data?.records || []) : []
  } catch {
    allOrders.value = []
  } finally {
    refreshing.value = false
    loading.value = false
  }
}

async function handleCancel(o) {
  try {
    const res = await cancelOrder(o.orderNo)
    if (res.code === 200) {
      showSuccessToast('订单已取消')
      loadData()
    }
  } catch (e) {
    toastOnce(e, '取消失败')
  }
}

function handlePay(o) {
  if (Number(o.amount || 0) <= 0) {
    showToast('该订单应付 0 元，无需支付')
    return
  }
  payingOrder.value = o
  payAmount.value = money(o.amount)
  payVisible.value = true
}

async function onPaySelect(action) {
  payVisible.value = false
  const order = payingOrder.value
  if (!order) return
  try {
    // 1) 创建支付记录（@RequestParam：orderId/userId/amount/payType）
    const createRes = await createPayment(order.id, userId, order.amount, action.value)
    if (createRes.code !== 200) return
    const payNo = createRes.data?.payNo
    if (!payNo) {
      showToast('支付流水号生成失败')
      return
    }
    // 2) 模拟第三方支付回调
    const payRes = action.value === 1 ? await wechatPay(payNo) : await alipayPay(payNo)
    if (payRes.code === 200) {
      showSuccessToast(payRes.data?.message || '支付成功')
      loadData()
    }
  } catch (e) {
    toastOnce(e, '支付失败')
  }
}
</script>

<style scoped>
.order + .order { margin-top: var(--sp-gap-card); }

.order__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 12px;
  margin-top: var(--sp-3);
  padding: 12px;
  background: var(--sp-surface-2);
  border-radius: var(--sp-radius-btn);
}
.order__cell { display: flex; flex-direction: column; gap: 2px; }
.order__k { font-size: 11px; color: var(--sp-text-3); }
.order__v { font-size: 13px; font-weight: 600; color: var(--sp-text); }

.order__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
}
.order__amount { color: var(--sp-text); }
.order__amount span { font-size: 12px; margin-right: 2px; }
.order__amount b {
  font-family: var(--sp-font-num);
  font-size: 20px;
  font-weight: 700;
}
.order__btns { display: flex; align-items: center; gap: var(--sp-2); }

.minibtn {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-text-2);
  background: var(--sp-surface-3);
  border: none;
  border-radius: var(--sp-radius-pill);
}
.minibtn--primary {
  color: #fff;
  background: var(--sp-grad-brand);
  box-shadow: 0 6px 16px rgba(76, 111, 168, 0.28);
}
.minibtn:active { opacity: 0.8; }

.paid { font-size: 12px; color: var(--sp-text-3); }

.order__no {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--sp-divider);
  font-size: 11px;
  color: var(--sp-text-3);
}
</style>
