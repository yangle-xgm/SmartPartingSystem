<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="My Parking"
      title="我的订单"
      desc="这里汇总了绑定车辆的全部停车记录。出场后系统自动结算费用，在线支付成功即完成闭环。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="$router.push('/reserve')">预约车位</el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="绑定车辆" :value="vehicleCount" unit="辆" icon="Van" tone="brand" hint="车牌加密存储，页面脱敏展示" />
      <StatCard label="停车中" :value="parkingCount" unit="笔" icon="Clock" tone="teal" hint="出场前请完成缴费" />
      <StatCard label="待缴费" :value="pendingCount" unit="笔" icon="Wallet" tone="warning" hint="逾期将影响下次入场" />
      <StatCard label="累计支出" :value="money(totalPaid)" unit="元" icon="Money" tone="gold" hint="仅统计已支付订单" />
    </div>

    <div class="sp-toolbar">
      <el-radio-group v-model="tab" @change="() => {}">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="parking">停车中</el-radio-button>
        <el-radio-button value="pending">待缴费</el-radio-button>
        <el-radio-button value="done">已完成</el-radio-button>
      </el-radio-group>
      <div class="sp-toolbar__right">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="handleSearch">
          <el-option label="进行中" :value="0" />
          <el-option label="已完成" :value="1" />
          <el-option label="已取消" :value="2" />
        </el-select>
      </div>
    </div>

    <section class="sp-card orders-wrap" v-loading="loading">
      <template v-if="filtered.length">
        <article v-for="o in filtered" :key="o.orderNo" class="order" :class="{ 'is-live': o.status === 0 }">
          <div class="order__head">
            <div class="order__left">
              <span v-if="displayPlate(o) !== '—'" class="sp-plate">{{ displayPlate(o) }}</span>
              <span v-else class="plate-unknown">车牌未同步</span>
              <div class="order__meta">
                <b>{{ o.spaceNo }} 车位</b>
                <span>{{ shortTime(o.entryTime) }} 入场<template v-if="o.exitTime"> · {{ shortTime(o.exitTime) }} 出场</template></span>
              </div>
            </div>
            <el-tag :type="tagOf(o)" effect="light">{{ statusText(o) }}</el-tag>
          </div>

          <div class="order__body">
            <div class="order__cell">
              <span>停车时长</span>
              <b class="sp-num">{{ o.status === 0 ? '进行中' : (o.duration != null ? o.duration + ' 分钟' : '—') }}</b>
            </div>
            <div class="order__cell">
              <span>应付金额</span>
              <b class="sp-num amount">{{ o.amount != null ? '¥' + money(o.amount) : '待结算' }}</b>
            </div>
            <div class="order__cell">
              <span>支付状态</span>
              <b>{{ payMap[o.payStatus] }}</b>
            </div>
            <div class="order__cell">
              <span>订单号</span>
              <b class="code">{{ o.orderNo }}</b>
            </div>
          </div>

          <div class="order__foot">
            <span class="order__tip">
              <template v-if="o.status === 0">离场前请完成缴费，出场道闸自动放行</template>
              <template v-else-if="isFree(o)">免费时长内出场，本单 0 元无需支付</template>
              <template v-else-if="o.status === 1 && o.payStatus === 0">已出场，请尽快完成支付</template>
              <template v-else-if="o.payStatus === 1">支付完成，感谢使用智慧星河</template>
              <template v-else>订单已取消</template>
            </span>
            <div class="order__actions">
              <el-button v-if="o.status === 0" type="success" plain size="small" @click="handleExit(o)">模拟出场</el-button>
              <el-tag v-else-if="isFree(o)" type="success" effect="plain" size="small">无需支付</el-tag>
              <el-button v-else-if="o.status === 1 && o.payStatus === 0" type="primary" size="small" @click="openPay(o)">去支付</el-button>
            </div>
          </div>
        </article>
      </template>
      <div v-else class="sp-empty">
        <span class="sp-empty__star">✦</span>
        <span class="sp-empty__text">{{ loading ? '加载中…' : '暂无符合条件的订单' }}</span>
      </div>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 16px"
      />
    </section>

    <el-dialog v-model="payVisible" title="在线缴费" width="430px">
      <div class="pay-head">
        <span>应付金额</span>
        <b class="sp-num">¥{{ money(payOrder.amount) }}</b>
      </div>
      <div class="pay-methods">
        <button
          v-for="m in payMethods"
          :key="m.value"
          class="pay-method"
          :class="{ 'is-active': payType === m.value }"
          @click="payType = m.value"
        >
          <span class="pay-method__icon" :style="{ background: m.color }">{{ m.short }}</span>
          <b>{{ m.name }}</b>
        </button>
      </div>
      <p class="pay-tip">支付成功后订单状态实时更新，出场道闸自动抬杆。</p>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="handleConfirmPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getOrderList, exitOrder } from '@/api/order'
import { getVehicleList } from '@/api/vehicle'
import { createPayment, wechatPay, alipayPay } from '@/api/payment'
import { isAlipaySandboxReady, payWithAlipaySandbox } from '@/utils/alipaySandbox'
import { getUserId } from '@/api/user'
import { displayPlate } from '@/utils/plate'

const loading = ref(false)
const paying = ref(false)
const tableData = ref([])
const tab = ref('all')
const statusFilter = ref('')
const vehicleCount = ref(0)
const payVisible = ref(false)
const payType = ref(1)
const payOrder = ref({})

const statusMap = { 0: '进行中', 1: '已完成', 2: '已取消' }
// 与后端 Constants 对齐：0 待支付 / 1 支付成功 / 2 已退款 / 3 已关闭（超时关单）/ 4 支付失败
const payMap = { 0: '未支付', 1: '已支付', 2: '已退款', 3: '已关闭', 4: '支付失败' }

const payMethods = [
  { value: 1, name: '微信支付', short: '微', color: 'linear-gradient(135deg,#3FBF6B,#1F9E52)' },
  { value: 2, name: '支付宝', short: '支', color: 'linear-gradient(135deg,#3A8BEF,#1B63C7)' }
]

const pagination = reactive({ current: 1, size: 10, total: 0 })

const money = (v) => (v === null || v === undefined || v === '' ? '0.00' : Number(v).toFixed(2))
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '—')

/** 免费时长内出场：已结算、金额 0、未支付 —— 不应再引导去支付（E2E 报告 P2-10） */
const isFree = (o) => o.status === 1 && o.payStatus === 0 && Number(o.amount || 0) <= 0

const parkingCount = computed(() => tableData.value.filter(o => o.status === 0).length)
const pendingCount = computed(() => tableData.value.filter(o => o.status === 1 && o.payStatus === 0 && !isFree(o)).length)
const totalPaid = computed(() => tableData.value.filter(o => o.payStatus === 1).reduce((s, o) => s + Number(o.amount || 0), 0))

const filtered = computed(() => {
  if (tab.value === 'parking') return tableData.value.filter(o => o.status === 0)
  if (tab.value === 'pending') return tableData.value.filter(o => o.status === 1 && o.payStatus === 0 && !isFree(o))
  if (tab.value === 'done') return tableData.value.filter(o => o.status === 1 && (o.payStatus === 1 || isFree(o)))
  return tableData.value
})

const statusText = (o) => {
  if (o.status === 0) return '停车中'
  if (o.status === 2) return '已取消'
  if (isFree(o)) return '无需支付'
  return o.payStatus === 1 ? '已完成' : '待缴费'
}

const tagOf = (o) => {
  if (o.status === 0) return 'warning'
  if (o.status === 2) return 'info'
  if (isFree(o)) return 'success'
  return o.payStatus === 1 ? 'success' : 'danger'
}

onMounted(() => loadData())

const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    // 后端对 role=0 会强制按本人车辆过滤，前端只做展示
    const params = { current: pagination.current, size: pagination.size }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const [orders, vehicles] = await Promise.allSettled([
      getOrderList(params),
      getVehicleList(userId)
    ])
    if (orders.status === 'fulfilled') {
      tableData.value = orders.value.data?.records || []
      pagination.total = orders.value.data?.total || 0
    }
    if (vehicles.status === 'fulfilled') vehicleCount.value = (vehicles.value.data || []).length
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }

const handleExit = async (row) => {
  try {
    await ElMessageBox.confirm(`确定车辆 ${displayPlate(row)} 出场并结算费用？`, '模拟出场', {
      type: 'warning',
      confirmButtonText: '确认出场',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  const res = await exitOrder(row.orderNo)
  ElMessage.success(`出场成功，应付 ¥${money(res.data?.amount)}`)
  loadData()
}

const openPay = (row) => {
  if (Number(row.amount || 0) <= 0) {
    ElMessage.info('该订单应付 0 元，无需支付')
    return
  }
  payOrder.value = row
  payType.value = 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  paying.value = true
  try {
    const createRes = await createPayment({
      orderId: payOrder.value.id,
      userId: getUserId(),
      amount: payOrder.value.amount,
      payType: payType.value
    })
    const payNo = createRes.data?.payNo
    if (!payNo) throw new Error('支付流水创建失败')
    // 支付宝沙箱：跳转收银台 + 轮询确认到账
    if (payType.value === 2 && (await isAlipaySandboxReady())) {
      const result = await payWithAlipaySandbox(payNo)
      if (!result.ok) throw new Error(result.message || '支付未完成')
      ElMessage.success('支付宝支付成功')
      payVisible.value = false
      loadData()
      return
    }
    const payRes = payType.value === 1 ? await wechatPay(payNo) : await alipayPay(payNo)
    ElMessage.success(payRes.data?.message || '支付成功')
    payVisible.value = false
    loadData()
  } catch (e) {
    toastOnce(e, '支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.plate-unknown {
  font-size: 11.5px;
  color: var(--sp-text-3);
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px dashed var(--sp-card-border);
}

.orders-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order {
  border: 1px solid var(--sp-card-border);
  border-radius: 14px;
  padding: 16px;
  background: var(--sp-surface);
  transition: border-color var(--sp-dur-micro) var(--sp-ease), box-shadow var(--sp-dur-micro) var(--sp-ease);
}

.order:hover {
  border-color: var(--sp-brand);
  box-shadow: var(--sp-shadow-hover);
}

.order.is-live {
  border-left: 3px solid var(--sp-teal);
}

.order__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.order__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.order__meta b {
  display: block;
  font-size: 13.5px;
  color: var(--sp-text);
}

.order__meta span {
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.order__body {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 14px 0 12px;
}

.order__cell {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--sp-surface-2);
}

.order__cell span {
  display: block;
  font-size: 11px;
  color: var(--sp-text-3);
}

.order__cell b {
  display: block;
  margin-top: 4px;
  font-size: 13.5px;
  color: var(--sp-text);
  font-weight: 700;
}

.order__cell .amount {
  color: var(--sp-gold);
}

.order__cell .code {
  font-size: 11px;
  font-weight: 500;
  color: var(--sp-text-3);
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  word-break: break-all;
}

.order__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.order__tip {
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.order__actions {
  display: flex;
  gap: 8px;
}

/* 支付弹窗 */
.pay-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--sp-gold-soft);
  margin-bottom: 14px;
}

.pay-head span {
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.pay-head b {
  font-size: 26px;
  font-weight: 800;
  color: var(--sp-gold);
}

.pay-methods {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.pay-method {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  cursor: pointer;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.pay-method:hover {
  border-color: var(--sp-brand);
}

.pay-method.is-active {
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
}

.pay-method__icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.pay-method b {
  font-size: 13px;
  color: var(--sp-text);
}

.pay-tip {
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

@media (max-width: 900px) {
  .order__body {
    grid-template-columns: repeat(2, 1fr);
  }

  .order__foot {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
