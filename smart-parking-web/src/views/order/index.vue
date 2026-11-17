<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Order Center"
      title="订单管理"
      desc="停车订单由设备联动自动生成：入场锁位 → 出场结算 → 支付回调闭环。支持按状态筛选、费用重算与模拟出场。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="今日入场" :value="dash.todayEntry" unit="车次" icon="Bottom" tone="brand" />
      <StatCard label="今日出场" :value="dash.todayExit" unit="车次" icon="Top" tone="teal" />
      <StatCard label="当前在场" :value="dash.currentParked" unit="辆" icon="Van" tone="warning" />
      <StatCard label="今日收入" :value="money(dash.todayIncome)" unit="元" icon="Money" tone="gold" />
    </div>

    <div class="sp-toolbar">
      <el-radio-group v-model="searchForm.status" @change="handleSearch">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="0">进行中</el-radio-button>
        <el-radio-button :value="1">已完成</el-radio-button>
        <el-radio-button :value="2">已取消</el-radio-button>
      </el-radio-group>
      <div class="sp-toolbar__right">
        <el-input v-model="keyword" placeholder="搜索车牌 / 订单号" clearable style="width: 200px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="filteredData" v-loading="loading" stripe>
        <el-table-column label="订单号" width="196">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="车牌" width="128">
          <template #default="{ row }">
            <span v-if="displayPlate(row) !== '—'" class="sp-plate">{{ displayPlate(row) }}</span>
            <span v-else class="muted">车牌未同步</span>
          </template>
        </el-table-column>
        <el-table-column prop="spaceNo" label="车位" width="92" />
        <el-table-column label="入场时间" width="160">
          <template #default="{ row }">{{ shortTime(row.entryTime) }}</template>
        </el-table-column>
        <el-table-column label="出场时间" width="160">
          <template #default="{ row }">{{ shortTime(row.exitTime) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="92">
          <template #default="{ row }">
            <span class="sp-num">{{ row.duration ? row.duration + ' 分' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <b v-if="row.amount != null" class="amount sp-num">¥{{ money(row.amount) }}</b>
            <span v-else class="muted">待结算</span>
          </template>
        </el-table-column>
        <el-table-column label="支付" width="100">
          <template #default="{ row }">
            <el-tag :type="payTag[row.payStatus]" effect="light">{{ payMap[row.payStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 0 ? 'book' : row.status === 1 ? 'free' : 'used'" />
              {{ statusMap[row.status] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" text type="success" @click="handleExit(row)">模拟出场</el-button>
            <el-button
              v-else-if="row.status === 1 && row.payStatus === 0 && Number(row.amount) > 0"
              size="small"
              text
              type="warning"
              @click="openPay(row)"
            >去支付</el-button>
            <span v-else-if="row.status === 1 && row.payStatus === 0" class="muted">0 元免支付</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无订单</span>
          </div>
        </template>
      </el-table>

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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="440px">
      <div v-if="detail" class="detail">
        <div class="detail__hero">
          <span class="sp-plate">{{ displayPlate(detail) }}</span>
          <div class="detail__hero-r">
            <b class="sp-num" v-if="detail.amount != null">¥{{ money(detail.amount) }}</b>
            <b v-else>待结算</b>
            <el-tag size="small" :type="payTag[detail.payStatus]" effect="light">{{ payMap[detail.payStatus] }}</el-tag>
          </div>
        </div>

        <div class="sp-kv">
          <div class="sp-kv__item">
            <p class="sp-kv__k">订单状态</p>
            <p class="sp-kv__v">{{ statusMap[detail.status] }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">车位号</p>
            <p class="sp-kv__v">{{ detail.spaceNo }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">入场时间</p>
            <p class="sp-kv__v small">{{ shortTime(detail.entryTime) }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">出场时间</p>
            <p class="sp-kv__v small">{{ shortTime(detail.exitTime) }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">停车时长</p>
            <p class="sp-kv__v">{{ detail.duration ? detail.duration + ' 分钟' : '—' }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">支付时间</p>
            <p class="sp-kv__v small">{{ shortTime(detail.payTime) }}</p>
          </div>
        </div>

        <div class="detail__code">订单号 {{ detail.orderNo }}</div>

        <div class="detail__actions">
          <el-button v-if="detail.status === 0" type="success" @click="handleExit(detail)">模拟出场并结算</el-button>
          <el-button
            v-else-if="detail.status === 1 && detail.payStatus === 0 && Number(detail.amount) > 0"
            type="primary"
            @click="openPay(detail)"
          >发起支付</el-button>
          <span v-else-if="detail.status === 1 && detail.payStatus === 0" class="muted">应付 0 元，无需生成支付流水</span>
        </div>
      </div>
    </el-drawer>

    <!-- 支付弹窗 -->
    <el-dialog v-model="payVisible" title="订单支付" width="440px">
      <div class="pay-head">
        <span>应付金额</span>
        <b class="sp-num">¥{{ money(payOrder.amount) }}</b>
      </div>
      <div class="sp-kv">
        <div class="sp-kv__item">
          <p class="sp-kv__k">订单号</p>
          <p class="sp-kv__v small">{{ payOrder.orderNo }}</p>
        </div>
        <div class="sp-kv__item">
          <p class="sp-kv__k">车牌</p>
          <p class="sp-kv__v">{{ displayPlate(payOrder) }}</p>
        </div>
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
      <p class="pay-tip">演示环境：点击确认支付后将调用支付服务模拟回调，订单状态实时同步。</p>
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
import { getOrderList, getOrderDetail, exitOrder } from '@/api/order'
import { createPayment, wechatPay, alipayPay } from '@/api/payment'
import { getDashboardData } from '@/api/statistics'
import { getUserId } from '@/api/user'
import { displayPlate } from '@/utils/plate'

const loading = ref(false)
const paying = ref(false)
const tableData = ref([])
const dash = ref({})
const keyword = ref('')
const detailVisible = ref(false)
const detail = ref(null)
const payVisible = ref(false)
const payType = ref(1)
const payOrder = ref({})

const statusMap = { 0: '进行中', 1: '已完成', 2: '已取消' }
// 与后端 Constants 对齐：0 待支付 / 1 支付成功 / 2 已退款 / 3 已关闭（超时关单）/ 4 支付失败
const payMap = { 0: '未支付', 1: '已支付', 2: '已退款', 3: '已关闭', 4: '支付失败' }
const payTag = { 0: 'warning', 1: 'success', 2: 'info', 3: 'info', 4: 'danger' }

const payMethods = [
  { value: 1, name: '微信支付', short: '微', color: 'linear-gradient(135deg,#3FBF6B,#1F9E52)' },
  { value: 2, name: '支付宝', short: '支', color: 'linear-gradient(135deg,#3A8BEF,#1B63C7)' }
]

const searchForm = reactive({ status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const money = (v) => (v === null || v === undefined || v === '' ? '0.00' : Number(v).toFixed(2))
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

// 车牌/订单号搜索在前端做（后端 ParkingOrderQuery 未提供关键字字段）
const filteredData = computed(() => {
  const k = keyword.value.trim().toUpperCase()
  if (!k) return tableData.value
  return tableData.value.filter(r =>
    String(r.orderNo || '').toUpperCase().includes(k) || String(r.plateNo || '').toUpperCase().includes(k)
  )
})

onMounted(() => {
  loadData()
  loadDash()
})

const loadDash = async () => {
  try {
    const res = await getDashboardData()
    dash.value = res.data || {}
  } catch (e) {
    dash.value = {}
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (searchForm.status !== '') params.status = searchForm.status
    const res = await getOrderList(params)
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.status = ''; keyword.value = ''; handleSearch() }

const openDetail = async (row) => {
  // 先展示列表行数据，再拉取最新详情，避免抽屉空窗
  detail.value = row
  detailVisible.value = true
  try {
    const res = await getOrderDetail(row.orderNo)
    if (res.data) detail.value = res.data
  } catch (e) { /* 保留列表数据 */ }
}

const handleExit = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定为车辆 ${displayPlate(row)} 出场并结算费用？结算后将生成应付金额。`,
      '模拟出场',
      { type: 'warning', confirmButtonText: '确认出场', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const res = await exitOrder(row.orderNo)
  ElMessage.success(`出场成功，应付 ¥${money(res.data?.amount)}`)
  detailVisible.value = false
  loadData()
  loadDash()
}

const openPay = (row) => {
  if (Number(row.amount || 0) <= 0) {
    ElMessage.info('应付金额为 0，无需发起支付')
    return
  }
  payOrder.value = row
  payType.value = 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  if (payOrder.value.amount == null) {
    ElMessage.warning('订单金额未结算，请先出场')
    return
  }
  if (Number(payOrder.value.amount) <= 0) {
    ElMessage.info('应付金额为 0，无需生成支付流水')
    return
  }
  paying.value = true
  try {
    // 1. 创建支付流水（POST /api/payment/create 走 @RequestParam）
    const createRes = await createPayment({
      orderId: payOrder.value.id,
      userId: getUserId(),
      amount: payOrder.value.amount,
      payType: payType.value
    })
    const payNo = createRes.data?.payNo
    if (!payNo) throw new Error('支付流水创建失败')
    // 2. 模拟渠道支付
    const payRes = payType.value === 1 ? await wechatPay(payNo) : await alipayPay(payNo)
    ElMessage.success(payRes.data?.message || '支付成功')
    payVisible.value = false
    detailVisible.value = false
    loadData()
    loadDash()
  } catch (e) {
    toastOnce(e, '支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.order-no {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--sp-text-2);
}

.amount {
  color: var(--sp-gold);
  font-weight: 700;
}

.muted {
  color: var(--sp-text-3);
  font-size: 12px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

/* 详情 */
.detail__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 14px;
  background: var(--sp-surface-2);
  margin-bottom: 14px;
}

.detail__hero-r {
  text-align: right;
}

.detail__hero-r b {
  display: block;
  font-size: 22px;
  font-weight: 800;
  color: var(--sp-gold);
  margin-bottom: 4px;
}

.detail__code {
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--sp-text-3);
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  word-break: break-all;
}

.detail__actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}

.sp-kv__v.small {
  font-size: 12.5px;
  font-weight: 600;
}

/* 支付 */
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
  margin-top: 16px;
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
  box-shadow: 0 0 0 3px var(--sp-brand-soft);
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
  line-height: 1.6;
}
</style>
