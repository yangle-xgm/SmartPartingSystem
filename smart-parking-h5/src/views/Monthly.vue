<template>
  <div class="h-page monthly">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">MONTHLY PASS</span>
          <h1 class="h-hero__title">月卡充值</h1>
          <p class="h-hero__desc">固定车位包月，进出不按次计费</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ monthlyCount }}</b>
          <span>月卡车辆</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ tempCount }}</b>
          <span>临停车辆</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <b>{{ quote ? daysLeft(quote.expireAfter) : '-' }}</b>
          <span>充值后可用天数</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-section">
        <span class="h-section__title">选择车辆</span>
        <span class="h-section__more" @click="loadVehicles">刷新</span>
      </div>

      <template v-if="loading">
        <div v-for="n in 2" :key="`sk-${n}`" class="h-sk h-sk--card" />
      </template>
      <template v-else-if="vehicles.length">
        <div
          v-for="v in vehicles"
          :key="v.id"
          class="h-card h-card--tap pick"
          :class="{ 'pick--on': String(v.id) === String(vehicleId) }"
          @click="selectVehicle(v)"
        >
          <div class="pick__row">
            <span class="h-plate" :data-color="v.plateColor || '蓝'">
              {{ v.plateNoMasked || v.plateNo }}
            </span>
            <span class="h-pill" :data-tone="pillTone(v)">
              {{ pillText(v) }}
            </span>
          </div>
          <div class="pick__meta">
            {{ v.brand || '未填写品牌' }} · 月卡到期
            <b :class="{ warn: isSoon(v.monthlyExpire) }">{{ dateOnly(v.monthlyExpire) || '未开通' }}</b>
          </div>
        </div>
      </template>
      <div v-else class="h-card h-empty">
        <van-icon name="logistics" class="h-empty__icon" />
        <div class="h-empty__title">还没有绑定车辆</div>
        <div class="h-empty__hint">先在「我的车辆」绑定车牌，才能开通月卡</div>
        <button class="h-empty__cta" type="button" @click="router.push('/vehicles')">
          去绑定车辆
        </button>
      </div>

      <template v-if="vehicleId">
        <div class="h-section">
          <span class="h-section__title">购买时长</span>
          <span class="h-section__more">单价 ¥{{ money(unitPrice) }} / 月</span>
        </div>

        <div class="mopt">
          <div
            v-for="m in monthOptions"
            :key="m.value"
            class="mopt__item"
            :class="{ 'mopt__item--on': m.value === months }"
            @click="selectMonths(m.value)"
          >
            <b>{{ m.value }}</b>
            <span>{{ m.label }}</span>
          </div>
        </div>

        <div class="h-card bill">
          <div class="bill__row">
            <span>月单价</span>
            <b>¥{{ money(unitPrice) }}</b>
          </div>
          <div class="bill__row">
            <span>购买时长</span>
            <b>{{ months }} 个月</b>
          </div>
          <div class="bill__row">
            <span>当前到期</span>
            <b>{{ dateOnly(quote?.expireBefore) || (quote?.isRenewal ? '-' : '未开通') }}</b>
          </div>
          <div class="bill__row bill__row--hi">
            <span>充值后到期</span>
            <b>{{ dateOnly(quote?.expireAfter) || '计算中…' }}</b>
          </div>
          <div class="bill__total">
            <span>应付金额</span>
            <b><i>¥</i>{{ money(quote?.amount ?? 0) }}</b>
          </div>
        </div>

        <p class="tip">
          月卡生效后，该车在包月期内进出固定车位不计次收费；
          临时车位与访客车辆仍按临时车标准计费。
        </p>
      </template>
    </div>

    <!-- 底部支付栏 -->
    <div v-if="vehicleId" class="paybar">
      <div class="paybar__sum">
        <span>合计</span>
        <b><i>¥</i>{{ money(quote?.amount ?? 0) }}</b>
      </div>
      <button class="h-btn paybar__btn" type="button" :disabled="!canPay" @click="openPay">
        {{ quote?.isRenewal ? '立即续费' : '立即开通' }}
      </button>
    </div>

    <!-- 支付方式 -->
    <van-action-sheet
      v-model:show="payVisible"
      :title="`支付 ¥${money(quote?.amount ?? 0)}`"
      :actions="payActions"
      cancel-text="取消"
      close-on-click-action
      @select="onPaySelect"
    />

    <van-loading v-if="paying" class="paying" type="spinner" color="#fff" size="24">
      支付处理中…
    </van-loading>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { toastOnce } from '@/utils/request'
import { showSuccessToast, showToast } from 'vant'
import { getVehicleList, quoteMonthly, createMonthlyOrder, payMonthly } from '@/api/vehicle'
import { getUserId } from '@/api/user'

const router = useRouter()
const route = useRoute()
const userId = getUserId()

const vehicles = ref([])
const loading = ref(true)
const vehicleId = ref(null)
const months = ref(1)
const quote = ref(null)
const quoting = ref(false)
const payVisible = ref(false)
const paying = ref(false)

const monthOptions = [
  { value: 1, label: '1 个月' },
  { value: 3, label: '3 个月' },
  { value: 6, label: '6 个月' },
  { value: 12, label: '12 个月' }
]

const payActions = [
  { name: '微信支付（模拟）', subname: '演示环境，确认即支付成功', value: 1 },
  { name: '支付宝（模拟）', subname: '演示环境，确认即支付成功', value: 2 }
]

const monthlyCount = computed(() => vehicles.value.filter((v) => !!v.monthlyExpire).length)
const tempCount = computed(() => vehicles.value.filter((v) => !v.monthlyExpire).length)
const unitPrice = computed(() => Number(quote.value?.unitPrice ?? 0))
const canPay = computed(() => !!quote.value && !quoting.value && !paying.value)

onMounted(loadVehicles)

async function loadVehicles() {
  try {
    const res = await getVehicleList(userId)
    if (res.code === 200) {
      // 访客车辆(type=1)不能开月卡，过滤掉避免误操作
      vehicles.value = (Array.isArray(res.data) ? res.data : []).filter((v) => v.type !== 1)
      // 从车辆页带过来的 vehicleId 优先（雪花 ID 序列化为字符串，统一按字符串比）
      const wanted = route.query.vehicleId
      const hit = wanted != null ? vehicles.value.find((v) => String(v.id) === String(wanted)) : null
      if (hit) {
        vehicleId.value = hit.id
      } else if (!vehicles.value.some((v) => String(v.id) === String(vehicleId.value))) {
        vehicleId.value = vehicles.value[0]?.id ?? null
      }
      if (vehicleId.value) await loadQuote()
    }
  } catch (e) {
    toastOnce(e, '车辆加载失败')
  } finally {
    loading.value = false
  }
}

async function loadQuote() {
  if (!vehicleId.value) return
  quoting.value = true
  try {
    const res = await quoteMonthly(vehicleId.value, months.value)
    if (res.code === 200) quote.value = res.data
  } catch (e) {
    quote.value = null
    toastOnce(e, '报价获取失败')
  } finally {
    quoting.value = false
  }
}

function selectVehicle(v) {
  if (String(vehicleId.value) === String(v.id)) return
  vehicleId.value = v.id
  loadQuote()
}

function selectMonths(m) {
  if (months.value === m) return
  months.value = m
  loadQuote()
}

function openPay() {
  if (!canPay.value) return
  payVisible.value = true
}

async function onPaySelect(action) {
  if (!quote.value) return
  paying.value = true
  try {
    const created = await createMonthlyOrder(vehicleId.value, months.value, action.value)
    if (created.code !== 200) return
    const orderNo = created.data?.orderNo
    if (!orderNo) {
      showToast('月卡订单创建失败')
      return
    }
    const paid = await payMonthly(orderNo)
    if (paid.code === 200) {
      showSuccessToast(paid.data?.message || '充值成功')
      await loadVehicles()
    }
  } catch (e) {
    toastOnce(e, '充值失败')
  } finally {
    paying.value = false
  }
}

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/mine')
}

// ==================== 展示辅助 ====================

function dateOnly(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 10)
}

function daysLeft(v) {
  if (!v) return '-'
  const t = new Date(String(v).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return '-'
  const d = Math.ceil((t - Date.now()) / 86400000)
  return d > 0 ? d : 0
}

function isSoon(v) {
  if (!v) return false
  const t = new Date(String(v).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return false
  return t - Date.now() < 30 * 86400000
}

function pillTone(v) {
  if (!v.monthlyExpire) return 'muted'
  return isSoon(v.monthlyExpire) ? 'warning' : 'teal'
}

function pillText(v) {
  if (!v.monthlyExpire) return '临停'
  return isSoon(v.monthlyExpire) ? '即将到期' : '月卡有效'
}

function money(v) {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}
</script>

<style scoped>
.monthly { padding-bottom: 116px; }

.pick { position: relative; }
.pick--on {
  box-shadow: 0 0 0 2px var(--sp-brand) inset;
}
.pick__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pick__meta {
  margin-top: 10px;
  font-size: 12px;
  color: var(--sp-text-2);
}
.pick__meta b { color: var(--sp-text); font-weight: 600; }
.warn { color: var(--sp-warning) !important; }

/* 购买时长 */
.mopt {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}
.mopt__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 12px 0;
  background: var(--sp-surface);
  border: 1px solid var(--sp-divider);
  border-radius: var(--sp-radius-btn);
  transition: all var(--sp-dur-micro) var(--sp-ease);
}
.mopt__item b { font-size: 18px; font-family: var(--sp-font-num); }
.mopt__item span { font-size: 11px; color: var(--sp-text-3); }
.mopt__item--on {
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}
.mopt__item--on b { color: var(--sp-brand); }
.mopt__item--on span { color: var(--sp-brand); }
.mopt__item:active { transform: scale(0.97); }

/* 报价明细 */
.bill { padding-top: 6px; padding-bottom: 6px; }
.bill__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  color: var(--sp-text-2);
  border-bottom: 1px dashed var(--sp-divider);
}
.bill__row b { color: var(--sp-text); font-weight: 600; }
.bill__row--hi b { color: var(--sp-teal); }
.bill__total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0 10px;
}
.bill__total span { font-size: 13px; color: var(--sp-text-2); }
.bill__total b {
  font-size: 22px;
  font-family: var(--sp-font-num);
  color: var(--sp-danger);
}
.bill__total i { font-style: normal; font-size: 13px; margin-right: 2px; }

.tip {
  margin: var(--sp-3) 0 var(--sp-4);
  font-size: 12px;
  line-height: 1.7;
  color: var(--sp-text-3);
}

/* 底部支付栏 */
.paybar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  /* 底部留 24px：小米/UC 等浏览器有底部工具栏时是覆盖式，
     fixed bottom:0 会被工具栏压住合计金额（实测小米 23127PN0CC） */
  padding: 10px var(--sp-pad-page) calc(24px + env(safe-area-inset-bottom));
  background: var(--sp-surface);
  border-top: 1px solid var(--sp-divider);
  z-index: 50;
}
.paybar__sum {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  /* 金额不能被按钮挤压换行：窄屏下曾经把 ¥360.00 拆成两行 */
  flex-shrink: 0;
  min-width: 88px;
  white-space: nowrap;
}
.paybar__sum span { font-size: 11px; color: var(--sp-text-3); }
.paybar__sum b {
  font-size: 20px;
  font-family: var(--sp-font-num);
  color: var(--sp-danger);
  white-space: nowrap;
}
.paybar__sum i { font-style: normal; font-size: 12px; margin-right: 1px; }
.paybar__btn { flex: 1; }

.paying {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(14, 23, 40, 0.45);
  color: #fff;
  font-size: 13px;
  z-index: 300;
}
</style>
