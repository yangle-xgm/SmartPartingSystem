<template>
  <div class="notify-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="通知公告" name="notice" />
      <el-tab-pane v-if="isOwner" label="我的反馈" name="feedback" />
      <el-tab-pane v-else :label="`业主反馈${unreadFeedbackCount ? '(' + unreadFeedbackCount + ')' : ''}`" name="feedback" />
    </el-tabs>

    <!-- 通知公告 -->
    <div v-show="activeTab === 'notice'">
      <el-card shadow="never" class="search-card">
        <el-form :inline="true" :model="searchForm">
          <el-form-item label="通知类型">
            <el-select v-model="searchForm.type" placeholder="全部" clearable style="width: 160px">
              <el-option label="入场通知" :value="1" />
              <el-option label="出场通知" :value="2" />
              <el-option label="缴费提醒" :value="3" />
              <el-option label="系统公告" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetSearch">重置</el-button>
            <el-button v-if="isOwner" type="success" @click="handleMarkAll">全部已读</el-button>
            <el-button v-if="!isOwner" type="danger" @click="showSendDialog = true">发送通知</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <el-table :data="tableData" v-loading="loading" border stripe :row-class-name="rowClassName">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ 'unread-title': row.isRead === 0 }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="110">
            <template #default="{ row }">
              <el-tag :type="typeTagMap[row.type]">{{ typeMap[row.type] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isRead" label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="!isOwner && row.userId === 1" type="success">已发出</el-tag>
              <el-tag v-else :type="row.isRead === 0 ? 'danger' : 'info'">{{ row.isRead === 0 ? '未读' : '已读' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="通知时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.isRead === 0 && row.userId !== 1" size="small" type="primary" @click="handleMarkRead(row)">标记已读</el-button>
              <span v-else style="color: #c0c4cc; font-size: 13px;">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="pagination.current" v-model:page-size="pagination.size"
          :total="pagination.total" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData"
          style="margin-top: 16px; justify-content: flex-end;" />
      </el-card>
    </div>

    <!-- 业主反馈 -->
    <div v-show="activeTab === 'feedback'">
      <el-card shadow="never" class="search-card">
        <el-form :inline="true">
          <el-form-item v-if="!isOwner" label="状态">
            <el-select v-model="fbStatus" placeholder="全部" clearable style="width:140px">
              <el-option label="待处理" :value="0" />
              <el-option label="已回复" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadFeedback">查询</el-button>
            <el-button v-if="isOwner" type="success" @click="showFbDialog = true">提交反馈</el-button>
          </el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <el-table :data="fbList" v-loading="fbLoading" border stripe>
          <el-table-column prop="type" label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="fbTypeTag[row.type]">{{ fbTypeMap[row.type] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" />
          <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'warning' : 'success'">{{ row.status === 0 ? '待处理' : '已回复' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reply" label="管理员回复" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="!isOwner" size="small" type="primary" @click="openReply(row)">回复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 发送通知弹窗 -->
    <el-dialog v-model="showSendDialog" title="发送系统通知" width="500px">
      <el-form :model="sendForm" label-width="80px">
        <el-form-item label="通知类型">
          <el-select v-model="sendForm.type" style="width:100%">
            <el-option label="缴费提醒" :value="3" />
            <el-option label="系统公告" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="sendForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="sendForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="接收对象">
          <el-radio-group v-model="sendForm.target">
            <el-radio :value="0">全体业主</el-radio>
            <el-radio :value="1">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="sendForm.target === 1" label="用户ID">
          <el-input v-model="sendForm.userId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
      </template>
    </el-dialog>

    <!-- 提交反馈弹窗 -->
    <el-dialog v-model="showFbDialog" title="提交反馈" width="500px">
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="fbForm.type" style="width:100%">
            <el-option label="投诉" :value="1" />
            <el-option label="建议" :value="2" />
            <el-option label="咨询" :value="3" />
            <el-option label="紧急" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="fbForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="fbForm.content" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFbDialog = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>

    <!-- 回复反馈弹窗 -->
    <el-dialog v-model="replyDialog" title="回复反馈" width="500px">
      <el-descriptions :column="1" border style="margin-bottom:12px">
        <el-descriptions-item label="标题">{{ currentFb.title }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ currentFb.content }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="replyText" type="textarea" :rows="3" placeholder="请输入回复" />
      <template #footer>
        <el-button @click="replyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifyList, markNotifyRead, markAllNotifyRead } from '@/api/notify'
import { getUserId } from '@/api/user'
import request from '@/utils/request'

const activeTab = ref('notice')
const loading = ref(false)
const tableData = ref([])
const isOwner = ref(false)
const showSendDialog = ref(false)
const sending = ref(false)
const sendForm = ref({ type: 4, title: '', content: '', target: 0, userId: null })

const typeMap = { 1: '入场通知', 2: '出场通知', 3: '缴费提醒', 4: '系统公告' }
const typeTagMap = { 1: 'primary', 2: 'success', 3: 'warning', 4: 'danger' }
const searchForm = reactive({ type: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

// feedback
const fbList = ref([])
const fbLoading = ref(false)
const fbStatus = ref(null)
const unreadFeedbackCount = ref(0)
const showFbDialog = ref(false)
const replyDialog = ref(false)
const currentFb = ref({})
const replyText = ref('')
const fbForm = ref({ type: 3, title: '', content: '' })
const fbTypeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const fbTypeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }

onMounted(() => {
  isOwner.value = getRole() === 0
  loadData()
  loadFeedback()
})

const getRole = () => {
  const token = localStorage.getItem('token')
  if (!token) return null
  try { return JSON.parse(atob(token.split('.')[1])).role } catch { return null }
}

const handleTabChange = (tab) => { if (tab === 'feedback') loadFeedback() }

const buildParams = () => {
  const params = { current: pagination.current, size: pagination.size, ...(searchForm.type !== '' ? { type: searchForm.type } : {}) }
  if (isOwner.value) params.userId = getUserId()
  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getNotifyList(buildParams())
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally { loading.value = false }
}

const loadFeedback = async () => {
  fbLoading.value = true
  try {
    const url = isOwner.value ? '/notify/feedback/my' : '/notify/feedback/all'
    const params = isOwner.value ? { userId: getUserId() } : { status: fbStatus.value }
    const res = await request({ url, method: 'get', params })
    fbList.value = res.data.records || res.data || []
    if (!isOwner.value) unreadFeedbackCount.value = fbList.value.filter(f => f.status === 0).length
  } finally { fbLoading.value = false }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.type = ''; handleSearch() }
const handleMarkRead = async (row) => { await markNotifyRead(row.id); ElMessage.success('已标记'); loadData() }
const handleMarkAll = async () => { await markAllNotifyRead(getUserId()); ElMessage.success('全部已读'); loadData() }
const rowClassName = ({ row }) => (row.isRead === 0 ? 'unread-row' : '')

const handleSend = async () => {
  if (!sendForm.value.title || !sendForm.value.content) { ElMessage.warning('请填写完整'); return }
  sending.value = true
  try {
    await request({ url: '/notify/admin/send', method: 'post', data: sendForm.value })
    ElMessage.success('通知已发送')
    showSendDialog.value = false
    loadData()
  } finally { sending.value = false }
}

const submitFeedback = async () => {
  if (!fbForm.value.title || !fbForm.value.content) { ElMessage.warning('请填写完整'); return }
  await request({ url: '/notify/feedback', method: 'post', data: { ...fbForm.value, userId: getUserId() } })
  ElMessage.success('反馈已提交')
  showFbDialog.value = false
  fbForm.value = { type: 3, title: '', content: '' }
  loadFeedback()
}

const openReply = (row) => { currentFb.value = row; replyText.value = row.reply || ''; replyDialog.value = true }
const submitReply = async () => {
  await request({ url: `/notify/feedback/${currentFb.value.id}/reply`, method: 'post', data: { reply: replyText.value } })
  ElMessage.success('回复成功')
  replyDialog.value = false
  loadFeedback()
}
</script>

<style lang="scss" scoped>
.search-card { margin-bottom: 16px; }
:deep(.unread-row) { background-color: rgba(123, 200, 176, 0.08); }
.unread-title { font-weight: 600; color: var(--sp-text); }
</style>
