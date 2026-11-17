<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <div>
          <span class="h-hero__eyebrow">NOTIFICATIONS</span>
          <h1 class="h-hero__title">通知中心</h1>
          <p class="h-hero__desc">入场、出场、缴费提醒与物业公告</p>
        </div>
        <button class="h-hero__action" type="button" @click="handleMarkAll">
          <van-icon name="passed" />全部已读
        </button>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ unreadCount }}</b>
          <span>未读消息</span>
        </div>
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ list.length }}</b>
          <span>全部消息</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ todayCount }}</b>
          <span>今日消息</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="chips">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          class="chips__item"
          :class="{ active: activeFilter === f.value }"
          @click="activeFilter = f.value"
        >{{ f.label }}<i v-if="f.value === 1 && unreadCount">{{ unreadCount }}</i></button>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="loading">
          <div v-for="n in 3" :key="`sk-${n}`" class="h-sk h-sk--card" />
        </template>
        <template v-else-if="visibleList.length">
          <div
            v-for="n in visibleList"
            :key="n.id"
            class="h-card h-card--tap msg"
            :class="{ 'msg--unread': n.isRead === 0 }"
            @click="handleRead(n)"
          >
            <div class="msg__icon" :data-tone="typeTone[n.type] || 'brand'">
              <van-icon :name="typeIcon[n.type] || 'bell'" />
            </div>
            <div class="msg__body">
              <div class="msg__head">
                <b class="msg__title">{{ n.title }}</b>
                <span class="msg__time">{{ shortTime(n.createTime) }}</span>
              </div>
              <p class="msg__content">{{ n.content }}</p>
              <span class="msg__type">{{ typeText[n.type] || '系统消息' }}</span>
            </div>
            <span v-if="n.isRead === 0" class="msg__dot" />
          </div>
        </template>
        <div v-else class="h-card h-empty">
          <van-icon name="bell" class="h-empty__icon" />
          {{ activeFilter === 1 ? '没有未读消息' : '暂无通知' }}
        </div>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showSuccessToast } from 'vant'
import { getNotifyList, markNotifyRead, markAllNotifyRead } from '@/api/notify'
import { getUserId } from '@/api/user'

const userId = getUserId()

const list = ref([])
const refreshing = ref(false)
/** 首屏加载态：避免「暂无通知」闪现后再跳出列表 */
const loading = ref(true)
const activeFilter = ref(0)

const filters = [
  { label: '全部', value: 0 },
  { label: '未读', value: 1 }
]

const typeIcon = { 1: 'logistics', 2: 'passed', 3: 'gold-coin-o', 4: 'volume-o' }
const typeTone = { 1: 'brand', 2: 'teal', 3: 'gold', 4: 'danger' }
const typeText = { 1: '入场通知', 2: '出场通知', 3: '缴费提醒', 4: '系统公告' }

const unreadCount = computed(() => list.value.filter(n => n.isRead === 0).length)
const todayCount = computed(() => {
  const d = new Date()
  const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return list.value.filter(n => String(n.createTime || '').slice(0, 10) === today).length
})
const visibleList = computed(() =>
  activeFilter.value === 1 ? list.value.filter(n => n.isRead === 0) : list.value)

function shortTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

onMounted(() => loadData())

async function loadData() {
  refreshing.value = true
  try {
    // 后端返回 PageResult，带 current/size
    const res = await getNotifyList({ userId, current: 1, size: 50 })
    list.value = res.code === 200 ? (res.data?.records || []) : []
  } catch {
    list.value = []
  } finally {
    refreshing.value = false
    loading.value = false
  }
}

async function handleRead(n) {
  if (n.isRead !== 0) return
  try {
    await markNotifyRead(n.id)
    n.isRead = 1
  } catch { /* 忽略已读失败，不阻塞浏览 */ }
}

async function handleMarkAll() {
  if (!unreadCount.value) {
    showSuccessToast('没有未读消息')
    return
  }
  try {
    const res = await markAllNotifyRead(userId)
    if (res.code === 200) {
      list.value.forEach(n => { n.isRead = 1 })
      showSuccessToast('已全部标记为已读')
    }
  } catch { /* ignore */ }
}
</script>

<style scoped>
.chips {
  display: flex;
  gap: 6px;
  margin-bottom: var(--sp-2);
}
.chips__item {
  height: 28px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--sp-text-2);
  background: var(--sp-card);
  border: 1px solid var(--sp-card-border);
  border-radius: var(--sp-radius-pill);
}
.chips__item i {
  font-style: normal;
  font-size: 10px;
  line-height: 14px;
  min-width: 14px;
  padding: 0 3px;
  text-align: center;
  color: #fff;
  background: var(--sp-danger);
  border-radius: 7px;
}
.chips__item.active {
  color: var(--sp-brand);
  font-weight: 700;
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}

.msg {
  position: relative;
  display: flex;
  gap: 10px;
}
.msg + .msg { margin-top: var(--sp-gap-card); }
.msg--unread {
  border-color: rgba(76, 111, 168, 0.28);
  box-shadow: 0 2px 14px rgba(76, 111, 168, 0.10);
}

.msg__icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
  border-radius: 10px;
}
.msg__icon[data-tone='teal'] { color: var(--sp-teal); background: var(--sp-teal-soft); }
.msg__icon[data-tone='gold'] { color: var(--sp-gold); background: var(--sp-gold-soft); }
.msg__icon[data-tone='danger'] { color: var(--sp-danger); background: var(--sp-danger-soft); }

.msg__body { flex: 1; min-width: 0; }
.msg__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.msg__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--sp-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg__time { flex-shrink: 0; font-size: 11px; color: var(--sp-text-3); }
.msg__content {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sp-text-2);
  word-break: break-word;
}
.msg__type {
  display: inline-block;
  margin-top: 8px;
  padding: 1px 8px;
  font-size: 10px;
  color: var(--sp-text-3);
  background: var(--sp-surface-3);
  border-radius: var(--sp-radius-pill);
}
.msg__dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sp-danger);
}
</style>
