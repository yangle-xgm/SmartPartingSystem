<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Audit Trail"
      title="操作审计"
      desc="网关与各业务服务通过内部接口写入审计日志，记录操作人、方法、参数摘要、结果与耗时，用于追溯与合规。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="日志总数" :value="pagination.total" unit="条" icon="Memo" tone="brand" hint="当前筛选条件下" />
      <StatCard label="本页成功" :value="pageStats.ok" unit="条" icon="CircleCheck" tone="teal" />
      <StatCard label="本页失败" :value="pageStats.fail" unit="条" icon="CircleClose" tone="danger" hint="需重点排查" />
      <StatCard label="本页平均耗时" :value="pageStats.avg" unit="ms" icon="Timer" tone="gold" hint="costTime 均值" />
    </div>

    <div class="sp-toolbar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="操作人">
          <el-input v-model="searchForm.username" placeholder="用户名" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.operation" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="op in operations" :key="op" :label="op" :value="op" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="searchForm.isSuccess" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 330px"
          />
        </el-form-item>
      </el-form>
      <div class="sp-toolbar__right">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="操作时间" width="160">
          <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.username || '系统' }}</template>
        </el-table-column>
        <el-table-column label="操作类型" width="140">
          <template #default="{ row }">
            <el-tag :type="operationTag(row.operation)" effect="light">{{ row.operation }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请求方法" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="code">{{ row.method || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.isSuccess === 1 ? 'free' : 'fix'" />
              {{ row.isSuccess === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            <span class="sp-num">{{ row.costTime != null ? row.costTime + ' ms' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="160" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ fmtIp(row.ip) }}</span></template>
        </el-table-column>
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <div class="params">
              <p class="params__k">参数摘要</p>
              <pre class="params__v">{{ pretty(row.params) }}</pre>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无审计日志</span>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getAuditList } from '@/api/audit'

const loading = ref(false)
const tableData = ref([])

const searchForm = reactive({ username: '', operation: '', isSuccess: '' })
const timeRange = ref(null)
const pagination = reactive({ current: 1, size: 10, total: 0 })

// 与后端实际写入的 operation 值保持一致
const operations = [
  '登录', '注册', '退出登录', '修改用户',
  '绑定车辆', '解绑车辆', '车牌识别', '车辆入场', '车辆出场',
  '取消订单', '发起支付', '支付成功', '生成访客邀请码', '访客凭码入场', '访客车牌识别入场'
]

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')

/**
 * D10/D11：历史记录的 IPv6 回环写法统一展示成 127.0.0.1。
 * 后端 WebRequestUtils 已从源头归一，这里兜住修复前落库的旧数据。
 */
const fmtIp = (ip) => {
  if (!ip) return '—'
  const v = String(ip).trim()
  if (v === '::1' || v === '0:0:0:0:0:0:0:1') return '127.0.0.1'
  if (v.toLowerCase().startsWith('::ffff:')) return v.slice(7)
  return v
}

const pageStats = computed(() => {
  const ok = tableData.value.filter(r => r.isSuccess === 1).length
  const cost = tableData.value.filter(r => r.costTime != null).map(r => Number(r.costTime))
  return {
    ok,
    fail: tableData.value.length - ok,
    avg: cost.length ? Math.round(cost.reduce((a, b) => a + b, 0) / cost.length) : 0
  }
})

const pretty = (v) => {
  if (!v) return '—'
  try {
    return JSON.stringify(JSON.parse(v), null, 2)
  } catch (e) {
    return v
  }
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.operation) params.operation = searchForm.operation
    if (searchForm.isSuccess !== '') params.isSuccess = searchForm.isSuccess
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res = await getAuditList(params)
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }

const resetSearch = () => {
  Object.assign(searchForm, { username: '', operation: '', isSuccess: '' })
  timeRange.value = null
  handleSearch()
}

const operationTag = (op) => {
  if (!op) return 'info'
  if (['登录', '退出登录', '注册'].includes(op)) return 'info'
  if (op.includes('入场') || op.includes('出场')) return 'success'
  if (op.includes('绑定') || op.includes('识别')) return 'warning'
  if (op.includes('支付')) return 'danger'
  if (op.includes('取消')) return 'primary'
  return 'info'
}
</script>

<style scoped>
/* D10：IP 用等宽字体并留足宽度，IPv6 不再被省略号截成 0:0:0:0:0:0:... */
.mono {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
}
.code {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.params {
  padding: 10px 18px 16px;
}

.params__k {
  font-size: 11px;
  color: var(--sp-text-3);
  letter-spacing: 0.1em;
  margin-bottom: 6px;
}

.params__v {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--sp-text-2);
  background: var(--sp-surface-2);
  border-radius: 10px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
