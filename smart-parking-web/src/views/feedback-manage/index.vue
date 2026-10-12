<template>
  <div class="feedback-manage-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="status" placeholder="全部" clearable style="width:140px">
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已回复" :value="2" />
            <el-option label="已关闭" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userName" label="业主" width="100" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.type]">{{ typeMap[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openReply(row)">回复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="回复反馈" width="500px">
      <el-descriptions :column="1" border style="margin-bottom:12px">
        <el-descriptions-item label="业主">{{ current.userName }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ current.content }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="replyText" type="textarea" :rows="3" placeholder="请输入回复内容" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="handleReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const tableData = ref([])
const status = ref(null)
const dialogVisible = ref(false)
const replying = ref(false)
const current = ref({})
const replyText = ref('')

const typeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const typeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }
const statusMap = { 0: '待处理', 1: '处理中', 2: '已回复', 3: '已关闭' }
const statusTag = { 0: 'warning', 1: 'primary', 2: 'success', 3: 'info' }

onMounted(loadData)

const loadData = async () => {
  loading.value = true
  try {
    const res = await request({ url: '/notify/feedback/all', method: 'get', params: { status: status.value } })
    tableData.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

const openReply = (row) => {
  current.value = row
  replyText.value = row.reply || ''
  dialogVisible.value = true
}

const handleReply = async () => {
  if (!replyText.value) { ElMessage.warning('请输入回复内容'); return }
  replying.value = true
  try {
    await request({ url: `/notify/feedback/${current.value.id}/reply`, method: 'post', data: { reply: replyText.value } })
    ElMessage.success('回复已发送')
    dialogVisible.value = false
    loadData()
  } finally {
    replying.value = false
  }
}
</script>
