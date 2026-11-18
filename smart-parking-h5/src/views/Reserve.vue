<template>
  <div class="h-page reserve">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">PARKING RESERVATION</span>
          <h1 class="h-hero__title">车位预约</h1>
          <p class="h-hero__desc">临时占位最长 2 小时，超时自动释放</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ available.length }}</b>
          <span>可预约车位</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ activeCount }}</b>
          <span>我的进行中</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <b>{{ 3 - activeCount }}</b>
          <span>剩余可约次数</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <van-tabs v-model:active="tab" class="rtabs" line-width="20px" swipeable>
        <van-tab title="可预约">
          <div class="area-chips">
            <span
              v-for="a in areas"
              :key="a"
              class="chip"
              :class="{ 'chip--on': a === area }"
              @click="selectArea(a)"
            >{{ a }}</span>
          </div>

          <template v-if="loading">
            <div v-for="n in 2" :key="`sk-${n}`" class="h-sk h-sk--card" />
          </template>
          <template v-else-if="filtered.length">
            <div v-for="s in filtered" :key="s.id" class="h-card sp">
              <div class="sp__head">
                <div class="sp__no">
                  <b>{{ s.spaceNo }}</b>
                  <span>{{ s.area }} 区 · {{ s.floor || 'B1' }}</span>
                </div>
                <span class="h-pill" data-tone="teal">可预约</span>
              </div>
              <div class="sp__foot">
                <span class="sp__price">{{ s.monthPrice ? `月租参考 ¥${money(s.monthPrice)}` : '临时车位' }}</span>
                <button class="h-btn sp__btn" type="button" @click="openReserve(s)">预约</button>
              </div>
            </div>
          </template>
          <div v-else class="h-card h-empty">
            <van-icon name="park" class="h-empty__icon" />
            <div class="h-empty__title">当前区域暂无可预约车位</div>
            <div class="h-empty__hint">换个区域看看，或稍后刷新重试</div>
          </div>
        </van-tab>

        <van-tab title="我的预约">
          <template v-if="loading">
            <div class="h-sk h-sk--card" />
          </template>
          <template v-else-if="reservations.length">
            <div v-for="r in reservations" :key="r.id" class="h-card res">
              <div class="res__head">
                <b>车位 #{{ r.spaceId }}</b>
                <span class="h-pill" :data-tone="resTone(r.status)">{{ resText(r.status) }}</span>
              </div>
              <div class="res__time">
                {{ fmt(r.startTime) }} → {{ fmt(r.endTime) }}
              </div>
              <div class="res__foot">
                <span class="res__no">预约号 {{ r.id }}</span>
                <button
                  v-if="r.status === 0"
                  class="minibtn minibtn--danger"
                  type="button"
                  @click="handleCancel(r)"
                >取消预约</button>
              </div>
            </div>
          </template>
          <div v-else class="h-card h-empty">
            <van-icon name="clock-o" class="h-empty__icon" />
            <div class="h-empty__title">还没有预约记录</div>
            <div class="h-empty__hint">在「可预约」里选一个车位试试</div>
          </div>
        </van-tab>
      </van-tabs>
    </div>

    <!-- 预约弹层 -->
    <van-popup v-model:show="sheetVisible" position="bottom" round :style="{ padding: '18px 16px 24px' }">
      <div class="sheet__title">预约 {{ picked?.spaceNo }}</div>
      <p class="sheet__desc">{{ picked?.area }} 区 · {{ picked?.floor || 'B1' }}，超时未入场车位将自动释放</p>

      <div class="opt-label">开始时间</div>
      <div class="opt">
        <span
          v-for="o in startOptions"
          :key="o.value"
          class="opt__item"
          :class="{ 'opt__item--on': o.value === startOffset }"
          @click="startOffset = o.value"
        >{{ o.label }}</span>
      </div>

      <div class="opt-label">占用时长</div>
      <div class="opt">
        <span
          v-for="d in durationOptions"
          :key="d.value"
          class="opt__item"
          :class="{ 'opt__item--on': d.value === duration }"
          @click="duration = d.value"
        >{{ d.label }}</span>
      </div>

      <div class="preview">
        <span>{{ fmt(preview.start) }}</span>
        <van-icon name="arrow" />
        <span>{{ fmt(preview.end) }}</span>
      </div>

      <button class="h-btn" style="margin-top: 18px;" :disabled="submitting" @click="submitReserve">
        {{ submitting ? '提交中…' : '确认预约' }}
      </button>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { toastOnce } from '@/utils/request'
import { showSuccessToast, showToast, showConfirmDialog } from 'vant'
import { getAvailableSpaces, reserveSpace, cancelReserve, getMyReservations } from '@/api/parking'

const router = useRouter()

const tab = ref(0)
const loading = ref(true)
const available = ref([])
const reservations = ref([])
const area = ref('全部')

const sheetVisible = ref(false)
const picked = ref(null)
const submitting = ref(false)
const startOffset = ref(1)   // 分钟
const duration = ref(120)    // 分钟

const startOptions = [
  { value: 1, label: '立即' },
  { value: 30, label: '30 分钟后' },
  { value: 60, label: '1 小时后' },
  { value: 120, label: '2 小时后' }
]

const durationOptions = [
  { value: 30, label: '30 分钟' },
  { value: 60, label: '1 小时' },
  { value: 120, label: '2 小时' }
]

const areas = computed(() => {
  const set = new Set(available.value.map((s) => s.area).filter(Boolean))
  return ['全部', ...Array.from(set).sort()]
})

const filtered = computed(() =>
  area.value === '全部' ? available.value : available.value.filter((s) => s.area === area.value)
)

const activeCount = computed(() => reservations.value.filter((r) => r.status === 0).length)

/** 预约预览：开始 = now + offset（向上取整到分钟），结束 = 开始 + 时长 */
const preview = computed(() => {
  const start = new Date(Date.now() + startOffset.value * 60000)
  start.setSeconds(0, 0)
  const end = new Date(start.getTime() + duration.value * 60000)
  return { start, end }
})

onMounted(loadAll)

async function loadAll() {
  try {
    const [av, my] = await Promise.allSettled([getAvailableSpaces(), getMyReservations()])
    if (av.status === 'fulfilled' && av.value.code === 200) {
      available.value = Array.isArray(av.value.data) ? av.value.data : []
    } else if (av.status === 'rejected') {
      toastOnce(av.reason, '车位加载失败')
    }
    if (my.status === 'fulfilled' && my.value.code === 200) {
      reservations.value = Array.isArray(my.value.data) ? my.value.data : []
    }
  } finally {
    loading.value = false
  }
}

function selectArea(a) {
  area.value = a
}

function openReserve(s) {
  if (activeCount.value >= 3) {
    showToast('进行中的预约已达上限（3 个），请先取消或等待入场')
    return
  }
  picked.value = s
  startOffset.value = 1
  duration.value = 120
  sheetVisible.value = true
}

async function submitReserve() {
  if (!picked.value) return
  submitting.value = true
  try {
    const res = await reserveSpace(picked.value.id, fmt(preview.value.start), fmt(preview.value.end))
    if (res.code === 200) {
      showSuccessToast('预约成功')
      sheetVisible.value = false
      tab.value = 1
      await loadAll()
    }
  } catch (e) {
    toastOnce(e, '预约失败')
  } finally {
    submitting.value = false
  }
}

async function handleCancel(r) {
  try {
    await showConfirmDialog({ title: '取消预约', message: `确定取消车位 #${r.spaceId} 的预约吗？` })
  } catch {
    return
  }
  try {
    const res = await cancelReserve(r.id)
    if (res.code === 200) {
      showSuccessToast('已取消')
      await loadAll()
    }
  } catch (e) {
    toastOnce(e, '取消失败')
  }
}

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/home')
}

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 后端按 LocalDateTime 解析（空格替换为 T），格式必须为 yyyy-MM-dd HH:mm:ss */
function fmt(v) {
  if (!v) return '-'
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 16)
  const d = v instanceof Date ? v : new Date(v)
  if (Number.isNaN(d.getTime())) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const RES = {
  0: { text: '待入场', tone: 'warning' },
  1: { text: '已入场', tone: 'teal' },
  2: { text: '已结束', tone: 'muted' },
  3: { text: '已取消', tone: 'muted' }
}
function resText(s) {
  return RES[s]?.text || '未知'
}
function resTone(s) {
  return RES[s]?.tone || 'muted'
}
function money(v) {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}
</script>

<style scoped>
.reserve { padding-bottom: calc(var(--sp-4) + env(safe-area-inset-bottom)); }

/* Vant Tabs 在深色 hero 下方的观感微调 */
.rtabs { margin: 0 calc(-1 * var(--sp-pad-page)); }
.rtabs :deep(.van-tabs__wrap) { padding: 0 var(--sp-pad-page); }
.rtabs :deep(.van-tab) { font-size: 14px; }
.rtabs :deep(.van-tabs__content) { padding: 12px var(--sp-pad-page) 0; }

.area-chips {
  display: flex;
  gap: var(--sp-2);
  overflow-x: auto;
  padding: 2px 0 10px;
  scrollbar-width: none;
}
.area-chips::-webkit-scrollbar { display: none; }
.chip {
  flex-shrink: 0;
  padding: 6px 14px;
  font-size: 12px;
  color: var(--sp-text-2);
  background: var(--sp-surface);
  border: 1px solid var(--sp-divider);
  border-radius: var(--sp-radius-pill);
}
.chip--on {
  color: #fff;
  background: var(--sp-brand);
  border-color: var(--sp-brand);
}

.sp__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.sp__no { display: flex; flex-direction: column; line-height: 1.25; }
.sp__no b { font-size: 19px; font-family: var(--sp-font-num); }
.sp__no span { font-size: 12px; color: var(--sp-text-3); margin-top: 2px; }
.sp__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--sp-divider);
}
.sp__price { font-size: 12px; color: var(--sp-text-2); }
.sp__btn {
  width: auto;
  height: 34px;
  padding: 0 22px;
  font-size: 13px;
}

.res__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.res__head b { font-size: 15px; }
.res__time {
  margin-top: 8px;
  font-size: 13px;
  color: var(--sp-text-2);
  font-family: var(--sp-font-num);
}
.res__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--sp-divider);
}
.res__no { font-size: 11px; color: var(--sp-text-3); }
.minibtn {
  height: 32px;
  padding: 0 16px;
  font-size: 12px;
  font-weight: 600;
  border: none;
  border-radius: var(--sp-radius-btn);
}
.minibtn--danger { color: var(--sp-danger); background: var(--sp-danger-soft); }
.minibtn:active { opacity: 0.75; }

/* 弹层 */
.sheet__title { font-size: 16px; font-weight: 700; }
.sheet__desc { margin: 6px 0 14px; font-size: 12px; color: var(--sp-text-3); }
.opt-label {
  margin: 14px 0 8px;
  font-size: 12px;
  color: var(--sp-text-3);
}
.opt { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
.opt__item {
  padding: 8px 14px;
  font-size: 13px;
  color: var(--sp-text-2);
  background: var(--sp-surface-3, #F2F4F7);
  border-radius: var(--sp-radius-btn);
}
.opt__item--on {
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
  font-weight: 600;
  box-shadow: 0 0 0 1px var(--sp-brand) inset;
}
.preview {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: 16px;
  padding: 12px;
  font-size: 13px;
  font-family: var(--sp-font-num);
  color: var(--sp-text);
  background: var(--sp-surface-3, #F2F4F7);
  border-radius: var(--sp-radius-btn);
}
</style>
