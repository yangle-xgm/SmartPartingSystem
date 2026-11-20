<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Billing"
      title="缴费管理"
      desc="支付流水与对账入口：每笔支付都有独立流水号与第三方交易号，支持微信 / 支付宝模拟渠道与退款状态追踪。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="待支付金额" :value="money(sum.pending)" unit="元" icon="Wallet" tone="warning" hint="超时将自动关闭订单" />
      <StatCard label="支付成功金额" :value="money(sum.paid)" unit="元" icon="CircleCheck" tone="teal" hint="本页统计范围" />
      <StatCard label="已退款金额" :value="money(sum.refund)" unit="元" icon="RefreshLeft" tone="brand" hint="退款回调自动同步订单" />
      <StatCard label="流水笔数" :value="pagination.total" unit="笔" icon="Tickets" tone="gold" hint="含全部支付渠道" />
    </div>

    <div class="sp-toolbar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="支付状态">
          <el-select v-model="searchForm.payStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="待支付" :value="0" />
            <el-option label="支付成功" :value="1" />
            <el-option label="已退款" :value="2" />
            <el-option label="已关闭" :value="3" />
            <el-option label="支付失败" :value="4" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="sp-toolbar__right">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="支付流水号" width="210">
          <template #default="{ row }">
            <span class="code">{{ row.payNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务单" width="190" show-overflow-tooltip>
          <template #default="{ row }"><span class="sp-num mono">{{ bizLabel(row) }}</span></template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <b class="amount sp-num">¥{{ money(row.amount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">
            <el-tag :type="row.payType === 1 ? 'success' : row.payType === 2 ? 'primary' : 'info'" effect="light">
              {{ payTypeMap[row.payType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.payStatus === 1 ? 'free' : row.payStatus === 0 ? 'book' : 'fix'" />
              {{ payStatusMap[row.payStatus] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="第三方交易号" min-width="180">
          <template #default="{ row }">
            <span class="code">{{ row.transactionId || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="160">
          <template #default="{ row }">{{ shortTime(row.payTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.payStatus === 0" size="small" text type="warning" @click="openPay(row)">去支付</el-button>
            <el-button size="small" text type="primary" @click="openBill(row)">账单</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无支付流水</span>
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

    <!-- 计费规则（只读）：让业主/物业在界面上就能看到扣费依据 -->
    <section class="sp-card rules-card">
      <div class="sp-card__head">
        <span class="sp-card__title">
          计费规则
          <span class="sp-card__sub">出场结算时按「车辆类型 + 车牌颜色」匹配，免费时长内应收 0 元</span>
        </span>
        <el-button text type="primary" size="small" :loading="rulesLoading" @click="loadRules">刷新</el-button>
      </div>

      <el-table :data="rules" size="small" v-loading="rulesLoading" stripe>
        <el-table-column prop="ruleName" label="规则名称" min-width="130" />
        <el-table-column label="适用车型" width="110">
          <template #default="{ row }">{{ vehicleTypeMap[row.vehicleType] || '全部' }}</template>
        </el-table-column>
        <el-table-column label="车牌颜色" width="100">
          <template #default="{ row }">{{ row.plateColor || '不限' }}</template>
        </el-table-column>
        <el-table-column label="免费时长" width="100">
          <template #default="{ row }">{{ row.freeMinutes != null ? row.freeMinutes + ' 分钟' : '—' }}</template>
        </el-table-column>
        <el-table-column label="计费单价" width="150">
          <template #default="{ row }">
            <span class="sp-num">{{ priceText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="每日封顶" width="110">
          <template #default="{ row }">{{ row.dailyCap != null ? '¥' + money(row.dailyCap) : '不封顶' }}</template>
        </el-table-column>
        <el-table-column label="月租价格" width="110">
          <template #default="{ row }">{{ row.monthlyPrice != null ? '¥' + money(row.monthlyPrice) : '—' }}</template>
        </el-table-column>
        <el-table-column label="周末价" min-width="150">
          <template #default="{ row }">
            <span v-if="row.weekendUnitPrice == null" class="muted">同平日</span>
            <span v-else class="sp-num">
              ¥{{ money(row.weekendUnitPrice) }}
              <i class="muted">/ 封顶 {{ row.weekendDailyCap != null ? '¥' + money(row.weekendDailyCap) : '不封顶' }}</i>
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">{{ rulesLoading ? '加载中…' : '暂无计费规则' }}</span>
          </div>
        </template>
      </el-table>
    </section>

    <!-- 支付弹窗 -->
    <el-dialog v-model="payVisible" title="确认支付" width="430px">
      <div class="pay-head">
        <span>支付金额</span>
        <b class="sp-num">¥{{ money(payRecord.amount) }}</b>
      </div>
      <div class="sp-kv">
        <div class="sp-kv__item">
          <p class="sp-kv__k">支付流水号</p>
          <p class="sp-kv__v small">{{ payRecord.payNo }}</p>
        </div>
        <div class="sp-kv__item">
          <p class="sp-kv__k">关联订单</p>
          <p class="sp-kv__v">#{{ payRecord.orderId }}</p>
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
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="handleConfirmPay">确认支付</el-button>
      </template>
    </el-dialog>

    <!-- 账单抽屉 -->
    <el-drawer v-model="billVisible" title="支付账单" size="400px">
      <div v-if="bill">
        <div class="bill-hero">
          <span>{{ payStatusMap[bill.payStatus] }}</span>
          <b class="sp-num">¥{{ money(bill.amount) }}</b>
          <i>{{ payTypeMap[bill.payType] }} · {{ shortTime(bill.payTime) }}</i>
        </div>
        <div class="sp-kv">
          <div class="sp-kv__item">
            <p class="sp-kv__k">支付流水号</p>
            <p class="sp-kv__v small">{{ bill.payNo }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">第三方交易号</p>
            <p class="sp-kv__v small">{{ bill.transactionId || '—' }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">关联订单 ID</p>
            <p class="sp-kv__v">{{ bill.orderId }}</p>
          </div>
          <div class="sp-kv__item">
            <p class="sp-kv__k">创建时间</p>
            <p class="sp-kv__v small">{{ shortTime(bill.createTime) }}</p>
          </div>
        </div>
        <el-alert type="info" :closable="false" show-icon style="margin-top: 16px">
          对账口径：以支付成功流水为收入依据，退款回调会同步回写订单与分润记录。
        </el-alert>
      </div>
      <div v-else class="sp-empty">
        <span class="sp-empty__star">✦</span>
        <span class="sp-empty__text">该订单暂无支付记录</span>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getPaymentList, getPaymentByOrder, wechatPay, alipayPay } from '@/api/payment'
import { getBillingRules } from '@/api/order'
import { getUserId, getRole } from '@/api/user'

const loading = ref(false)
const paying = ref(false)
const tableData = ref([])
const payVisible = ref(false)
const billVisible = ref(false)
const bill = ref(null)
const payType = ref(1)
const payRecord = ref({})

const rules = ref([])
const rulesLoading = ref(false)
const vehicleTypeMap = { 0: '全部车型', 1: '临时车', 2: '月租车' }

/** 「¥2.00 / 30 分钟」这种可读文案，避免直接甩 unitPrice/unitDuration 两个裸字段 */
const priceText = (r) => {
  if (r.unitPrice == null) return '—'
  const unit = r.unitDuration ? ` / ${r.unitDuration} 分钟` : ''
  return `¥${money(r.unitPrice)}${unit}`
}

const payTypeMap = { 1: '微信', 2: '支付宝', 3: '现金' }
// 月卡流水没有停车订单号，用业务单ID兜底，避免表格出现空单元格
const bizLabel = (row) => (row.bizType === 2 ? `月卡 #${row.bizId ?? '-'}` : (row.orderId ?? '-'))
// 后端 Constants：0 待支付 / 1 支付成功 / 2 已退款 / 3 已关闭（超时关单）/ 4 支付失败
const payStatusMap = { 0: '待支付', 1: '支付成功', 2: '已退款', 3: '已关闭', 4: '支付失败' }

const payMethods = [
  { value: 1, name: '微信支付', short: '微', color: 'linear-gradient(135deg,#3FBF6B,#1F9E52)' },
  { value: 2, name: '支付宝', short: '支', color: 'linear-gradient(135deg,#3A8BEF,#1B63C7)' }
]

const searchForm = reactive({ payStatus: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const money = (v) => (v === null || v === undefined || v === '' ? '0.00' : Number(v).toFixed(2))
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const sum = computed(() => {
  const s = { pending: 0, paid: 0, refund: 0 }
  tableData.value.forEach(r => {
    if (r.payStatus === 0) s.pending += Number(r.amount || 0)
    else if (r.payStatus === 1) s.paid += Number(r.amount || 0)
    else if (r.payStatus === 2) s.refund += Number(r.amount || 0)
  })
  return s
})

onMounted(() => {
  loadData()
  loadRules()
})

const loadRules = async () => {
  rulesLoading.value = true
  try {
    const res = await getBillingRules()
    rules.value = res.data || []
  } catch (e) {
    rules.value = []
  } finally {
    rulesLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (searchForm.payStatus !== '') params.payStatus = searchForm.payStatus
    // 业主端后端会强制按 X-User-Id 过滤，无需前端传 userId
    if (getRole() === 0) params.userId = getUserId()
    const res = await getPaymentList(params)
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.payStatus = ''; handleSearch() }

const openPay = (row) => {
  payRecord.value = row
  payType.value = row.payType === 2 ? 2 : 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  paying.value = true
  try {
    const res = payType.value === 1
      ? await wechatPay(payRecord.value.payNo)
      : await alipayPay(payRecord.value.payNo)
    ElMessage.success(res.data?.message || '支付成功')
    payVisible.value = false
    loadData()
  } catch (e) {
    toastOnce(e, '支付失败')
  } finally {
    paying.value = false
  }
}

const openBill = async (row) => {
  bill.value = row
  billVisible.value = true
  // 月卡流水没有停车订单号，列表数据已足够展示，不必再查账单
  if (!row.orderId) return
  try {
    // GET /api/payment/bill?orderId= ，订单无流水时后端返回 data=null
    const res = await getPaymentByOrder(row.orderId)
    if (res.data) bill.value = res.data
  } catch (e) { /* 保留列表数据 */ }
}
</script>

<style scoped>
/* D10：19 位雪花 ID 用等宽字体并允许 tooltip，避免在单元格里折成 3 行 */
.mono {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  letter-spacing: 0.01em;
  white-space: nowrap;
}
.code {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 11.5px;
  color: var(--sp-text-3);
  word-break: break-all;
}

.amount {
  color: var(--sp-gold);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

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

.sp-kv__v.small {
  font-size: 12px;
  font-weight: 600;
}

.bill-hero {
  padding: 18px;
  border-radius: 14px;
  background: var(--sp-surface-2);
  text-align: center;
  margin-bottom: 16px;
}

.bill-hero span {
  font-size: 11.5px;
  color: var(--sp-text-3);
  letter-spacing: 0.1em;
}

.bill-hero b {
  display: block;
  font-size: 30px;
  font-weight: 800;
  color: var(--sp-gold);
  margin: 4px 0;
}

.bill-hero i {
  font-style: normal;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

/* 计费规则 */
.rules-card {
  margin-top: var(--sp-4);
}

.rules-card .muted {
  font-style: normal;
  font-size: 11.5px;
  color: var(--sp-text-3);
}
</style>
