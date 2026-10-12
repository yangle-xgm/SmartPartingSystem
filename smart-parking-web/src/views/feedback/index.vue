<template>
  <div class="feedback-page">
    <el-card shadow="never" style="margin-bottom:16px">
      <el-button type="primary" @click="showDialog = true">我要反馈</el-button>
      <el-text type="info" style="margin-left:12px">遇到问题、建议或紧急情况可在这里联系物业，管理员会尽快回复</el-text>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.type]">{{ typeMap[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reply" label="物业回复" min-width="180">
          <template #default="{ row }">{{ row.reply || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
      </el-table>
    </el-card>

    <el-dialog v-model="showDialog" title="提交反馈" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="咨询" :value="3" />
            <el-option label="建议" :value="2" />
            <el-option label="投诉" :value="1" />
            <el-option label="紧急" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="简述问题" />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请描述遇到的问题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getUserId } from '@/api/user'

const loading = ref(false)
const tableData = ref([])
const showDialog = ref(false)
const submitting = ref(false)
const form = ref({ type: 3, title: '', content: '' })

const typeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const typeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }
const statusMap = { 0: '待处理', 1: '处理中', 2: '已回复', 3: '已关闭' }
const statusTag = { 0: 'warning', 1: 'primary', 2: 'success', 3: 'info' }

onMounted(loadData)

const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    const res = await request({ url: '/notify/feedback/my', method: 'get', params: { userId } })
    tableData.value = res.data.records || []
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.content) { ElMessage.warning('请填写标题和内容'); return }
  submitting.value = true
  try {
    await request({
      url: '/notify/feedback',
      method: 'post',
      data: { ...form.value, userId: getUserId(), userName: '业主' }
    })
    ElMessage.success('反馈已提交，物业会尽快处理')
    showDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>
