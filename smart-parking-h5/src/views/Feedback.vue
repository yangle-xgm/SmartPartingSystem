<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">FEEDBACK</span>
          <h1 class="h-hero__title">意见反馈</h1>
          <p class="h-hero__desc">报修、投诉与建议，物业回复后同步到这里</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ list.length }}</b>
          <span>我的反馈</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ repliedCount }}</b>
          <span>已回复</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ pendingCount }}</b>
          <span>待处理</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-card">
        <div class="h-card__head">
          <span class="h-card__title">提交反馈</span>
          <span class="h-card__extra">工作日 24 小时内响应</span>
        </div>

        <div class="types">
          <button
            v-for="t in types"
            :key="t.value"
            type="button"
            class="types__item"
            :class="{ active: form.type === t.value }"
            @click="form.type = t.value"
          >{{ t.label }}</button>
        </div>

        <van-field
          v-model="form.title"
          class="field"
          placeholder="标题（选填，不填自动取内容前 20 字）"
          maxlength="40"
        />
        <van-field
          v-model="form.content"
          class="field field--area"
          type="textarea"
          rows="4"
          autosize
          maxlength="500"
          show-word-limit
          placeholder="请描述遇到的问题，例如：B2 层 105 车位上方漏水、道闸不抬杆的时间与车牌"
        />

        <button
          class="h-btn submit"
          type="button"
          :disabled="submitting"
          @click="handleSubmit"
        >{{ submitting ? '提交中…' : '提交反馈' }}</button>
      </div>

      <div class="h-section">
        <span class="h-section__title">我的反馈</span>
        <span class="h-section__more" @click="loadData">刷新</span>
      </div>

      <template v-if="loading">
        <div v-for="n in 2" :key="`sk-${n}`" class="h-sk h-sk--card" />
      </template>
      <template v-else-if="list.length">
        <div v-for="f in list" :key="f.id" class="h-card fb">
          <div class="fb__head">
            <span class="fb__title">{{ f.title || '未填写标题' }}</span>
            <span class="h-pill" :data-tone="statusTone(f.status)">{{ statusText(f.status) }}</span>
          </div>
          <p class="fb__content">{{ f.content }}</p>
          <div class="fb__meta">
            <span>{{ typeText(f.type) }}</span>
            <span>{{ shortTime(f.createTime) }}</span>
          </div>
          <div v-if="f.reply" class="fb__reply">
            <span class="fb__reply-label"><van-icon name="chat-o" /> 物业回复</span>
            <p>{{ f.reply }}</p>
            <span class="fb__reply-time">{{ shortTime(f.replyTime) }}</span>
          </div>
        </div>
      </template>
      <div v-else class="h-card h-empty">
        <van-icon name="comment-o" class="h-empty__icon" />
        <div class="h-empty__title">还没有提交过反馈</div>
        <div class="h-empty__hint">车位、道闸、照明等问题都可以在这里反映</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { toastOnce } from '@/utils/request'
import { submitFeedback, getMyFeedback } from '@/api/notify'
import { getUserId } from '@/api/user'

const router = useRouter()
const userId = getUserId()

const types = [
  { label: '投诉', value: 1 },
  { label: '建议', value: 2 },
  { label: '咨询', value: 3 },
  { label: '紧急', value: 4 }
]

const form = ref({ type: 2, title: '', content: '' })
const submitting = ref(false)
const loading = ref(true)
const list = ref([])

const repliedCount = computed(() => list.value.filter(f => f.status === 2).length)
const pendingCount = computed(() => list.value.filter(f => f.status === 0 || f.status === 1).length)

function typeText(t) {
  return types.find(x => x.value === t)?.label || '咨询'
}

function statusText(s) {
  if (s === 1) return '处理中'
  if (s === 2) return '已回复'
  if (s === 3) return '已关闭'
  return '待处理'
}

function statusTone(s) {
  if (s === 2) return 'teal'
  if (s === 3) return 'muted'
  if (s === 1) return 'gold'
  return 'warning'
}

function shortTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

function goBack() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/mine')
}

onMounted(loadData)

async function loadData() {
  try {
    const res = await getMyFeedback({ userId, current: 1, size: 20 })
    list.value = res.code === 200 ? (res.data?.records || []) : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const content = form.value.content.trim()
  if (content.length < 5) {
    showToast('请至少描述 5 个字，方便物业定位问题')
    return
  }
  submitting.value = true
  try {
    // 后端 role=0 会忽略这里的 userId，改用网关注入身份
    const res = await submitFeedback({
      userId,
      type: form.value.type,
      title: form.value.title.trim(),
      content
    })
    if (res.code === 200) {
      showSuccessToast('已提交，物业会尽快处理')
      form.value = { type: form.value.type, title: '', content: '' }
      await loadData()
    }
  } catch (e) {
    toastOnce(e, '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.types {
  display: flex;
  gap: 6px;
  margin-bottom: var(--sp-3);
}
.types__item {
  flex: 1;
  height: 32px;
  font-size: 13px;
  color: var(--sp-text-2);
  background: var(--sp-surface-2);
  border: 1px solid transparent;
  border-radius: var(--sp-radius-btn);
}
.types__item.active {
  color: var(--sp-brand);
  font-weight: 700;
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}

/* van-field 默认带内边距与下划线，这里做成卡片内的输入块 */
.field {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--sp-surface-2);
  border-radius: var(--sp-radius-btn);
}
.field--area { padding-bottom: 6px; }

.submit {
  margin-top: var(--sp-4);
  height: 44px;
}

.fb + .fb { margin-top: var(--sp-gap-card); }
.fb__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.fb__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 700;
  color: var(--sp-text);
}
.fb__content {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sp-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
.fb__meta {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--sp-text-3);
}
.fb__reply {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--sp-teal-soft);
  border-radius: var(--sp-radius-btn);
}
.fb__reply-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--sp-teal);
}
.fb__reply p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sp-text);
  white-space: pre-wrap;
}
.fb__reply-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--sp-text-3);
}
</style>
