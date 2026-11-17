<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Notification"
      title="通知中心"
      desc="出入场通知、缴费提醒与系统公告统一在此查看；未读消息会同步到顶部铃铛角标（60 秒轮询）。"
    >
      <template #actions>
        <el-button v-if="isOwner" type="primary" plain @click="handleMarkAll">
          <el-icon><Select /></el-icon>全部已读
        </el-button>
        <el-button v-if="!isOwner" type="primary" @click="showSendDialog = true">
          <el-icon><Promotion /></el-icon>发送通知
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="通知公告" name="notice" />
      <el-tab-pane v-if="isOwner" label="我的反馈" name="feedback" />
      <el-tab-pane v-else :label="`业主反馈${pendingFb ? '（' + pendingFb + '）' : ''}`" name="feedback" />
    </el-tabs>

    <!-- ============ 通知 ============ -->
    <template v-if="activeTab === 'notice'">
      <div class="sp-toolbar">
        <el-select v-model="searchForm.type" placeholder="全部类型" clearable style="width: 150px" @change="handleSearch">
          <el-option label="入场通知" :value="1" />
          <el-option label="出场通知" :value="2" />
          <el-option label="缴费提醒" :value="3" />
          <el-option label="系统公告" :value="4" />
        </el-select>
        <span class="toolbar-tip">共 {{ pagination.total }} 条 · 未读 {{ unreadInPage }} 条</span>
        <div class="sp-toolbar__right">
          <el-button @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </div>
      </div>

      <section class="sp-card notice-card" v-loading="loading">
        <ul v-if="tableData.length" class="notice">
          <li v-for="n in tableData" :key="n.id" class="notice__item" :class="{ 'is-unread': n.isRead === 0 }">
            <span class="notice__icon" :data-type="n.type">
              <el-icon><component :is="typeIcon[n.type]" /></el-icon>
            </span>
            <div class="notice__main">
              <div class="notice__head">
                <b>{{ n.title }}</b>
                <el-tag size="small" :type="typeTagMap[n.type]" effect="light">{{ typeMap[n.type] }}</el-tag>
                <el-tag v-if="n.isRead === 0" size="small" type="danger" effect="plain">未读</el-tag>
              </div>
              <p class="notice__content">{{ n.content }}</p>
              <div class="notice__foot">
                <time>{{ shortTime(n.createTime) }}</time>
                <span v-if="!isOwner && n.userId === 1" class="notice__self">发送方副本</span>
              </div>
            </div>
            <div class="notice__actions">
              <el-button v-if="n.isRead === 0 && n.userId !== 1" size="small" text type="primary" @click="handleMarkRead(n)">
                标记已读
              </el-button>
            </div>
          </li>
        </ul>
        <div v-else class="sp-empty">
          <span class="sp-empty__star">✦</span>
          <span class="sp-empty__text">暂无通知</span>
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
    </template>

    <!-- ============ 反馈 ============ -->
    <template v-else>
      <div class="sp-toolbar">
        <el-select v-if="!isOwner" v-model="fbStatus" placeholder="全部状态" clearable style="width: 140px" @change="loadFeedback">
          <el-option label="待处理" :value="0" />
          <el-option label="已回复" :value="2" />
        </el-select>
        <span class="toolbar-tip">{{ isOwner ? '提交后物业会尽快回复' : '按提交时间倒序，优先处理待办' }}</span>
        <div class="sp-toolbar__right">
          <el-button v-if="isOwner" type="primary" @click="showFbDialog = true">
            <el-icon><EditPen /></el-icon>提交反馈
          </el-button>
          <el-button @click="loadFeedback">刷新</el-button>
        </div>
      </div>

      <section class="sp-card">
        <el-table :data="fbList" v-loading="fbLoading" stripe>
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <el-tag :type="fbTypeTag[row.type]" effect="light">{{ fbTypeMap[row.type] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="150" />
          <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'warning' : 'success'" effect="light">
                {{ row.status === 0 ? '待处理' : '已回复' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="物业回复" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reply || '—' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!isOwner" size="small" text type="primary" @click="openReply(row)">回复</el-button>
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
    </template>

    <!-- 发送通知 -->
    <el-dialog v-model="showSendDialog" title="发送系统通知" width="520px">
      <el-form :model="sendForm" label-width="88px">
        <el-form-item label="通知类型">
          <el-select v-model="sendForm.type" style="width: 100%">
            <el-option label="缴费提醒" :value="3" />
            <el-option label="系统公告" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="sendForm.title" placeholder="如 本周地库消杀通知" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sendForm.content" type="textarea" :rows="4" placeholder="请输入通知正文" maxlength="300" show-word-limit />
        </el-form-item>
        <el-form-item label="接收对象">
          <el-radio-group v-model="sendForm.target">
            <el-radio :value="0">全体业主</el-radio>
            <el-radio :value="1">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="sendForm.target === 1" label="用户 ID">
          <el-input v-model="sendForm.userId" placeholder="接收人的用户 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
      </template>
    </el-dialog>

    <!-- 提交反馈 -->
    <el-dialog v-model="showFbDialog" title="提交反馈" width="500px">
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="fbForm.type" style="width: 100%">
            <el-option label="投诉" :value="1" />
            <el-option label="建议" :value="2" />
            <el-option label="咨询" :value="3" />
            <el-option label="紧急" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="fbForm.title" placeholder="一句话说明问题" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="fbForm.content" type="textarea" :rows="4" placeholder="请描述遇到的问题、发生位置与时间" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFbDialog = false">取消</el-button>
        <el-button type="primary" :loading="fbSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>

    <!-- 回复反馈 -->
    <el-dialog v-model="replyDialog" title="回复反馈" width="520px">
      <div class="reply-quote">
        <b>{{ currentFb.title }}</b>
        <p>{{ currentFb.content }}</p>
      </div>
      <el-input v-model="replyText" type="textarea" :rows="4" placeholder="请输入处理结果或说明" maxlength="300" show-word-limit />
      <template #footer>
        <el-button @click="replyDialog = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { getNotifyList, markNotifyRead, markAllNotifyRead, sendAdminNotify } from '@/api/notify'
import { getRole, getUserId } from '@/api/user'
import { useNotifyStore } from '@/store/notify'
import request from '@/utils/request'

const notifyStore = useNotifyStore()

const activeTab = ref('notice')
const loading = ref(false)
const tableData = ref([])
const isOwner = ref(getRole() === 0)

const showSendDialog = ref(false)
const sending = ref(false)
const sendForm = ref({ type: 4, title: '', content: '', target: 0, userId: '' })

const typeMap = { 1: '入场通知', 2: '出场通知', 3: '缴费提醒', 4: '系统公告' }
const typeTagMap = { 1: 'primary', 2: 'success', 3: 'warning', 4: 'danger' }
const typeIcon = { 1: 'Bottom', 2: 'Top', 3: 'Wallet', 4: 'Bell' }

const searchForm = reactive({ type: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })
const unreadInPage = computed(() => tableData.value.filter(n => n.isRead === 0).length)

const fbList = ref([])
const fbLoading = ref(false)
const fbStatus = ref('')
const pendingFb = ref(0)
const showFbDialog = ref(false)
const fbSubmitting = ref(false)
const replyDialog = ref(false)
const replying = ref(false)
const currentFb = ref({})
const replyText = ref('')
const fbForm = ref({ type: 3, title: '', content: '' })
const fbTypeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const fbTypeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

onMounted(() => {
  loadData()
  loadFeedback()
})

const handleTabChange = (tab) => {
  if (tab === 'feedback') loadFeedback()
}

const buildParams = () => {
  const params = { current: pagination.current, size: pagination.size }
  if (searchForm.type !== '') params.type = searchForm.type
  if (isOwner.value) params.userId = getUserId()
  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getNotifyList(buildParams())
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const loadFeedback = async () => {
  fbLoading.value = true
  try {
    // 业主：/notify/feedback/my?userId=；管理员：/notify/feedback/all?status=
    const url = isOwner.value ? '/notify/feedback/my' : '/notify/feedback/all'
    const params = isOwner.value ? { userId: getUserId() } : { current: 1, size: 50 }
    if (!isOwner.value && fbStatus.value !== '') params.status = fbStatus.value
    const res = await request({ url, method: 'get', params })
    fbList.value = res.data?.records || []
    if (!isOwner.value) pendingFb.value = fbList.value.filter(f => f.status === 0).length
  } finally {
    fbLoading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.type = ''; handleSearch() }

const handleMarkRead = async (row) => {
  await markNotifyRead(row.id)
  ElMessage.success('已标记为已读')
  // D8：角标与列表同源刷新，不再等 60s 轮询
  notifyStore.decrement(1)
  loadData()
  notifyStore.refresh()
}

const handleMarkAll = async () => {
  await markAllNotifyRead(getUserId())
  ElMessage.success('全部标记为已读')
  loadData()
  notifyStore.refresh()
}

const handleSend = async () => {
  if (!sendForm.value.title || !sendForm.value.content) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  if (sendForm.value.target === 1 && !sendForm.value.userId) {
    ElMessage.warning('请填写接收用户 ID')
    return
  }
  sending.value = true
  try {
    // 统一走 api/notify.js 的 sendAdminNotify，避免视图里再手写一遍 URL
    await sendAdminNotify({
      title: sendForm.value.title,
      content: sendForm.value.content,
      type: sendForm.value.type,
      target: sendForm.value.target,
      userId: sendForm.value.target === 1 ? Number(sendForm.value.userId) : null
    })
    ElMessage.success('通知已发送')
    showSendDialog.value = false
    sendForm.value = { type: 4, title: '', content: '', target: 0, userId: '' }
    loadData()
  } finally {
    sending.value = false
  }
}

const submitFeedback = async () => {
  if (!fbForm.value.title || !fbForm.value.content) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  fbSubmitting.value = true
  try {
    await request({
      url: '/notify/feedback',
      method: 'post',
      data: { ...fbForm.value, userId: getUserId(), userName: '业主' }
    })
    ElMessage.success('反馈已提交')
    showFbDialog.value = false
    fbForm.value = { type: 3, title: '', content: '' }
    loadFeedback()
  } finally {
    fbSubmitting.value = false
  }
}

const openReply = (row) => {
  currentFb.value = row
  replyText.value = row.reply || ''
  replyDialog.value = true
}

const submitReply = async () => {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replying.value = true
  try {
    await request({
      url: `/notify/feedback/${currentFb.value.id}/reply`,
      method: 'post',
      data: { reply: replyText.value }
    })
    ElMessage.success('回复成功')
    replyDialog.value = false
    loadFeedback()
  } finally {
    replying.value = false
  }
}
</script>

<style scoped>
.toolbar-tip {
  font-size: 12px;
  color: var(--sp-text-3);
}

.notice-card {
  padding: var(--sp-4) var(--sp-5);
}

.notice {
  list-style: none;
  display: flex;
  flex-direction: column;
}

.notice__item {
  display: flex;
  gap: 12px;
  padding: 14px 10px;
  border-bottom: 1px dashed var(--sp-divider);
  border-radius: 10px;
  transition: background var(--sp-dur-micro) var(--sp-ease);
}

.notice__item:last-child {
  border-bottom: none;
}

.notice__item:hover {
  background: var(--sp-surface-2);
}

.notice__item.is-unread {
  background: var(--sp-brand-soft);
}

.notice__icon {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 16px;
  background: var(--sp-surface);
  border: 1px solid var(--sp-card-border);
  color: var(--sp-brand);
}

.notice__icon[data-type='1'] { color: var(--sp-teal); }
.notice__icon[data-type='2'] { color: var(--sp-brand); }
.notice__icon[data-type='3'] { color: var(--sp-warning); }
.notice__icon[data-type='4'] { color: var(--sp-gold); }

.notice__main {
  flex: 1;
  min-width: 0;
}

.notice__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notice__head b {
  font-size: 13.5px;
  color: var(--sp-text);
}

.notice__content {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--sp-text-2);
  line-height: 1.65;
}

.notice__foot {
  margin-top: 6px;
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--sp-text-3);
}

.notice__self {
  color: var(--sp-gold);
}

.notice__actions {
  flex: none;
  display: flex;
  align-items: center;
}

.reply-quote {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  margin-bottom: 12px;
}

.reply-quote b {
  font-size: 13px;
  color: var(--sp-text);
}

.reply-quote p {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sp-text-3);
  line-height: 1.6;
}

@media (max-width: 760px) {
  .notice__actions {
    display: none;
  }
}
</style>
