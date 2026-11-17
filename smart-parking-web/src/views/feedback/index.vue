<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Feedback"
      title="意见反馈"
      desc="遇到报修、收费疑问或服务建议都可以在这里提交，物业会在反馈处理列表中回复，回复内容实时同步到本页。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="openDialog">
          <el-icon><EditPen /></el-icon>提交反馈
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="我的反馈" :value="list.length" unit="条" icon="ChatDotRound" tone="brand" />
      <StatCard label="待处理" :value="pending" unit="条" icon="Clock" tone="warning" hint="物业处理中" />
      <StatCard label="已回复" :value="replied" unit="条" icon="CircleCheck" tone="teal" hint="点击查看回复" />
    </div>

    <div class="sp-toolbar">
      <el-radio-group v-model="filterType" size="default">
        <el-radio-button :value="''">全部</el-radio-button>
        <el-radio-button :value="1">投诉</el-radio-button>
        <el-radio-button :value="2">建议</el-radio-button>
        <el-radio-button :value="3">咨询</el-radio-button>
        <el-radio-button :value="4">紧急</el-radio-button>
      </el-radio-group>
    </div>

    <section class="sp-card" v-loading="loading">
      <div v-if="filtered.length" class="fb-list">
        <article v-for="f in filtered" :key="f.id" class="fb" :class="{ 'is-done': f.status !== 0 }">
          <div class="fb__head">
            <el-tag :type="typeTag[f.type]" effect="light">{{ typeMap[f.type] }}</el-tag>
            <b>{{ f.title }}</b>
            <el-tag :type="f.status === 0 ? 'warning' : 'success'" effect="plain" size="small">
              {{ f.status === 0 ? '待处理' : '已回复' }}
            </el-tag>
            <time>{{ shortTime(f.createTime) }}</time>
          </div>
          <p class="fb__content">{{ f.content }}</p>
          <div v-if="f.reply" class="fb__reply">
            <span class="fb__reply-label">物业回复</span>
            <p>{{ f.reply }}</p>
            <time v-if="f.replyTime">{{ shortTime(f.replyTime) }}</time>
          </div>
        </article>
      </div>
      <div v-else class="sp-empty">
        <span class="sp-empty__star">✦</span>
        <span class="sp-empty__text">{{ loading ? '加载中…' : '还没有提交过反馈' }}</span>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="提交反馈" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="类型" prop="type">
          <div class="type-picker">
            <button
              v-for="t in types"
              :key="t.value"
              type="button"
              class="type-item"
              :class="{ 'is-active': form.type === t.value }"
              @click="form.type = t.value"
            >
              <el-icon><component :is="t.icon" /></el-icon>
              <span>{{ t.label }}</span>
            </button>
          </div>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="一句话描述问题" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="详细描述" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请补充发生位置、时间与车牌等信息，便于物业快速定位"
            maxlength="300"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getUserId } from '@/api/user'
import request, { toastOnce } from '@/utils/request'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const filterType = ref('')
const formRef = ref(null)
const form = ref({ type: 3, title: '', content: '' })

const typeMap = { 1: '投诉', 2: '建议', 3: '咨询', 4: '紧急' }
const typeTag = { 1: 'danger', 2: 'success', 3: 'primary', 4: 'warning' }
const types = [
  { value: 1, label: '投诉', icon: 'Warning' },
  { value: 2, label: '建议', icon: 'Opportunity' },
  { value: 3, label: '咨询', icon: 'QuestionFilled' },
  { value: 4, label: '紧急', icon: 'AlarmClock' }
]

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写详细描述', trigger: 'blur' }]
}

const pending = computed(() => list.value.filter(f => f.status === 0).length)
const replied = computed(() => list.value.filter(f => f.status !== 0).length)
const filtered = computed(() => (filterType.value === '' ? list.value : list.value.filter(f => f.type === filterType.value)))
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

onMounted(() => loadData())

const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    // GET /api/notify/feedback/my?userId=&current=&size= → PageResult
    const res = await request({
      url: '/notify/feedback/my',
      method: 'get',
      params: { userId, current: 1, size: 100 }
    })
    list.value = res.data?.records || []
  } finally {
    loading.value = false
  }
}

const openDialog = () => {
  form.value = { type: 3, title: '', content: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const f = formRef.value
  if (!f) return
  await f.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // POST /api/notify/feedback，body 为 Feedback{type,title,content,userId,userName}
      await request({
        url: '/notify/feedback',
        method: 'post',
        data: {
          type: form.value.type,
          title: form.value.title,
          content: form.value.content,
          userId: getUserId(),
          userName: '业主'
        }
      })
      ElMessage.success('反馈已提交，物业会尽快处理')
      dialogVisible.value = false
      loadData()
    } catch (e) {
      toastOnce(e, '提交失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.fb-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fb {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  border-left: 3px solid var(--sp-warning);
}

.fb.is-done {
  border-left-color: var(--sp-teal);
}

.fb__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.fb__head b {
  font-size: 14px;
  color: var(--sp-text);
}

.fb__head time {
  margin-left: auto;
  font-size: 11px;
  color: var(--sp-text-3);
}

.fb__content {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--sp-text-2);
  line-height: 1.7;
}

.fb__reply {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--sp-teal-soft);
}

.fb__reply-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--sp-teal);
  letter-spacing: 0.08em;
}

.fb__reply p {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--sp-text);
  line-height: 1.7;
}

.fb__reply time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--sp-text-3);
}

.type-picker {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  width: 100%;
}

.type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border-radius: 10px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  color: var(--sp-text-2);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.type-item:hover {
  border-color: var(--sp-brand);
  color: var(--sp-brand);
}

.type-item.is-active {
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
  color: var(--sp-brand);
  font-weight: 600;
}
</style>
