<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Service Desk"
      title="反馈处理"
      desc="业主提交的投诉、建议与紧急事项集中处理，回复内容会实时同步到业主端「意见反馈」。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="全部反馈" :value="tableData.length" unit="条" icon="ChatLineRound" tone="brand" />
      <StatCard label="待处理" :value="pending" unit="条" icon="AlarmClock" tone="danger" hint="优先响应紧急事项" />
      <StatCard label="已回复" :value="tableData.length - pending" unit="条" icon="CircleCheck" tone="teal" />
      <StatCard label="紧急事项" :value="urgent" unit="条" icon="WarningFilled" tone="warning" hint="type = 4" />
    </div>

    <div class="sp-toolbar">
      <el-radio-group v-model="status" @change="loadData">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="2">已回复</el-radio-button>
      </el-radio-group>
      <div class="sp-toolbar__right">
        <el-input v-model="keyword" placeholder="搜索标题 / 内容" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="filtered" v-loading="loading" stripe>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.type]" effect="light">{{ typeMap[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业主" width="130">
          <template #default="{ row }">{{ row.userName || ('用户#' + row.userId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 0 ? 'book' : 'free'" />
              {{ row.status === 0 ? '待处理' : '已回复' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 0 ? 'primary' : 'info'" :plain="row.status !== 0" @click="openReply(row)">
              {{ row.status === 0 ? '回复' : '查看/再回复' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无反馈</span>
          </div>
        </template>
      </el-table>
    </section>

    <el-drawer v-model="drawerVisible" title="处理反馈" size="480px">
      <div v-if="current.id" class="fb-detail">
        <div class="fb-detail__head">
          <el-tag :type="typeTag[current.type]" effect="light">{{ typeMap[current.type] }}</el-tag>
          <b>{{ current.title }}</b>
        </div>
        <p class="fb-detail__meta">
          {{ current.userName || ('用户#' + current.userId) }} · {{ shortTime(current.createTime) }}
        </p>
        <p class="fb-detail__content">{{ current.content }}</p>

        <div v-if="current.reply" class="fb-detail__history">
          <span>上次回复</span>
          <p>{{ current.reply }}</p>
          <time v-if="current.replyTime">{{ shortTime(current.replyTime) }}</time>
        </div>

        <div class="fb-detail__form">
          <p class="fb-detail__label">回复内容</p>
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="5"
            placeholder="请说明处理结果、上门时间或后续安排"
            maxlength="300"
            show-word-limit
          />
          <div class="fb-detail__actions">
            <el-button @click="drawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="replying" @click="handleReply">提交回复</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import request from '@/utils/request'

const loading = ref(false)
const replying = ref(false)
const tableData = ref([])
const status = ref('')
const keyword = ref('')
const drawerVisible = ref(false)
const current = ref({})
const replyText = ref('')

const typeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const typeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const pending = computed(() => tableData.value.filter(f => f.status === 0).length)
const urgent = computed(() => tableData.value.filter(f => f.type === 4).length)

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return tableData.value
  return tableData.value.filter(f =>
    String(f.title || '').includes(k) || String(f.content || '').includes(k)
  )
})

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    // GET /api/notify/feedback/all?status=&current=&size=
    const params = { current: 1, size: 100 }
    if (status.value !== '') params.status = status.value
    const res = await request({ url: '/notify/feedback/all', method: 'get', params })
    tableData.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

const openReply = (row) => {
  current.value = row
  replyText.value = row.reply || ''
  drawerVisible.value = true
}

const handleReply = async () => {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    // POST /api/notify/feedback/{id}/reply，body {reply}
    await request({
      url: `/notify/feedback/${current.value.id}/reply`,
      method: 'post',
      data: { reply: replyText.value }
    })
    ElMessage.success('回复已发送')
    drawerVisible.value = false
    loadData()
  } finally {
    replying.value = false
  }
}
</script>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.fb-detail__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fb-detail__head b {
  font-size: 16px;
  color: var(--sp-text);
}

.fb-detail__meta {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.fb-detail__content {
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  font-size: 13px;
  color: var(--sp-text);
  line-height: 1.75;
}

.fb-detail__history {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--sp-teal-soft);
}

.fb-detail__history span {
  font-size: 11px;
  font-weight: 700;
  color: var(--sp-teal);
  letter-spacing: 0.08em;
}

.fb-detail__history p {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--sp-text);
  line-height: 1.7;
}

.fb-detail__history time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--sp-text-3);
}

.fb-detail__form {
  margin-top: 18px;
}

.fb-detail__label {
  font-size: 12px;
  color: var(--sp-text-2);
  margin-bottom: 6px;
}

.fb-detail__actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
