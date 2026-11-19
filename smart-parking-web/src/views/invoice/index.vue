<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Invoice Center"
      title="电子发票"
      desc="业主提交的停车费开票申请。开票金额取自实际支付流水，一单只能开一张；红冲后该订单不再重开。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="待开票" :value="pendingCount" unit="张" icon="AlarmClock" tone="warning" hint="业主已提交，等待开具" />
      <StatCard label="已开票" :value="issuedCount" unit="张" icon="CircleCheck" tone="teal" />
      <StatCard label="已红冲" :value="redCount" unit="张" icon="RefreshLeft" tone="danger" />
      <StatCard label="开票金额" :value="issuedAmount" unit="元" icon="Money" tone="brand" hint="已开票合计" />
    </div>

    <div class="sp-toolbar">
      <el-radio-group v-model="status" @change="loadData">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="0">待开票</el-radio-button>
        <el-radio-button :value="1">已开票</el-radio-button>
        <el-radio-button :value="2">已红冲</el-radio-button>
      </el-radio-group>
      <div class="sp-toolbar__right">
        <el-input
          v-model="keyword"
          placeholder="搜索抬头 / 发票号 / 税号"
          clearable
          style="width: 220px"
          @keyup.enter="loadData"
          @clear="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ shortTime(row.applyTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="抬头" min-width="200">
          <template #default="{ row }">
            <div class="inv-title">
              <el-tag :type="row.titleType === 2 ? 'primary' : 'info'" effect="light" size="small">
                {{ row.titleType === 2 ? '企业' : '个人' }}
              </el-tag>
              <b>{{ row.title }}</b>
            </div>
            <div v-if="row.taxNo" class="inv-tax">税号 {{ row.taxNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }"><b class="amount">¥{{ money(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column label="关联订单" width="110">
          <template #default="{ row }">#{{ row.orderId }}</template>
        </el-table-column>
        <el-table-column label="接收邮箱" min-width="170">
          <template #default="{ row }">
            <span v-if="row.email">{{ row.email }}</span>
            <span v-else class="muted">未填写</span>
          </template>
        </el-table-column>
        <el-table-column label="发票号" width="180">
          <template #default="{ row }">
            <span v-if="row.invoiceNo" class="invoice-no">{{ row.invoiceNo }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="dotTone(row.status)" />
              {{ statusText[row.status] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="primary" plain @click="handleIssue(row)">
              开具
            </el-button>
            <el-button v-else-if="row.status === 1" size="small" type="danger" plain @click="handleRedFlush(row)">
              红冲
            </el-button>
            <span v-else class="muted">已终结</span>
          </template>
        </el-table-column>
      </el-table>
      <p class="tip">
        红冲需要填写原因（如「抬头填写错误」），原因会写入发票备注并记入审计日志。
        本系统只做开票单据流转，不对接税务平台；正式开票需在税控系统完成后再回填发票号。
      </p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getInvoiceList, issueInvoice, redFlushInvoice } from '@/api/payment'

const loading = ref(false)
const status = ref('')
const keyword = ref('')
const rows = ref([])

const statusText = { 0: '待开票', 1: '已开票', 2: '已红冲' }
const dotTone = (s) => (s === 1 ? 'free' : s === 2 ? 'fix' : 'book')

const money = (v) => Number(v ?? 0).toFixed(2)
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const pendingCount = computed(() => rows.value.filter(r => r.status === 0).length)
const issuedCount = computed(() => rows.value.filter(r => r.status === 1).length)
const redCount = computed(() => rows.value.filter(r => r.status === 2).length)
const issuedAmount = computed(() =>
  rows.value.filter(r => r.status === 1).reduce((s, r) => s + (Number(r.amount) || 0), 0).toFixed(2))

const loadData = async () => {
  loading.value = true
  try {
    const params = {}
    if (status.value !== '') params.status = status.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const res = await getInvoiceList(params)
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

const handleIssue = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认为「${row.title}」开具 ¥${money(row.amount)} 的电子发票？`,
      '开具发票',
      { type: 'info', confirmButtonText: '确认开具', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const res = await issueInvoice(row.id)
  ElMessage.success(`已开具，发票号 ${res.data?.invoiceNo || ''}`)
  loadData()
}

const handleRedFlush = async (row) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写红冲原因（会记入审计日志）', '红冲发票', {
      confirmButtonText: '确认红冲',
      cancelButtonText: '取消',
      inputPlaceholder: '如：抬头填写错误',
      inputValidator: (v) => (v && v.trim().length >= 2) || '请填写至少 2 个字的原因'
    })
    reason = value
  } catch {
    return
  }
  await redFlushInvoice(row.id, reason)
  ElMessage.success('已红冲')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.inv-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.inv-title b {
  font-size: 13px;
  color: var(--sp-text);
}

.inv-tax {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.amount {
  font-family: var(--sp-font-num);
  font-size: 13.5px;
  color: var(--sp-text);
}

.invoice-no {
  font-family: var(--sp-font-num);
  font-size: 12px;
  color: var(--sp-text-2);
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

.tip {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  font-size: 12px;
  color: var(--sp-text-2);
  line-height: 1.7;
}
</style>
