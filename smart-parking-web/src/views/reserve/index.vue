<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Reservation"
      title="车位预约"
      desc="实时车位地图按区域展示空闲、占用、预约与维护状态；选中空闲车位即可锁定时段，避免到现场无位可停。"
    >
      <template #actions>
        <el-switch
          v-model="onlyFree"
          inline-prompt
          active-text="仅空闲"
          inactive-text="全部"
          style="--el-switch-on-color: var(--sp-teal)"
          @change="loadSpaces"
        />
        <el-select v-model="area" placeholder="全部区域" clearable style="width: 130px" @change="loadSpaces">
          <el-option v-for="a in areas" :key="a" :label="a + ' 区'" :value="a" />
        </el-select>
        <el-button :loading="loading" @click="reloadAll">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="可预约车位" :value="counts.free" unit="个" icon="CircleCheck" tone="teal" hint="点击地图上的绿色车位即可预约" />
      <StatCard label="已被占用" :value="counts.used" unit="个" icon="Van" tone="brand" hint="含月卡与临停" />
      <StatCard label="预约锁定中" :value="counts.booked" unit="个" icon="Clock" tone="gold" hint="超时未入场自动释放" />
      <StatCard label="我的预约" :value="myActive.length" unit="个" icon="Tickets" tone="warning" hint="同时最多 3 个进行中预约" />
    </div>

    <div class="reserve-grid">
      <!-- 车位地图 -->
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">车位实时地图 <span class="sp-card__sub">点击绿色车位预约</span></span>
          <div class="sp-legend">
            <span class="sp-legend__item"><i class="sp-dot" data-tone="free" />空闲</span>
            <span class="sp-legend__item"><i class="sp-dot" data-tone="used" />占用</span>
            <span class="sp-legend__item"><i class="sp-dot" data-tone="book" />预约</span>
            <span class="sp-legend__item"><i class="sp-dot" data-tone="fix" />维护</span>
          </div>
        </div>

        <div v-if="areaGroups.length" class="lots" v-loading="loading">
          <div v-for="g in areaGroups" :key="g.area" class="lot">
            <div class="lot__head">
              <span class="lot__name">{{ g.area }} 区</span>
              <span class="lot__meta">空闲 {{ g.free }} / 共 {{ g.list.length }}</span>
            </div>
            <div class="lot__grid">
              <button
                v-for="s in g.list"
                :key="s.id"
                class="cell"
                :class="['cell--' + s.status, { 'is-clickable': s.status === 0 }]"
                :title="`${s.spaceNo} · ${statusMap[s.status]}`"
                @click="s.status === 0 ? openReserve(s) : null"
              >
                <b>{{ s.spaceNo }}</b>
                <i>{{ statusMap[s.status] }}</i>
              </button>
            </div>
          </div>
        </div>
        <div v-else class="sp-empty">
          <span class="sp-empty__star">✦</span>
          <span class="sp-empty__text">该区域暂无车位</span>
        </div>
      </section>

      <!-- 我的预约 -->
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">我的预约</span>
          <el-button text type="primary" size="small" @click="loadMyReservations">刷新</el-button>
        </div>

        <div v-if="myList.length" v-loading="myLoading" class="my-list">
          <article v-for="r in myList" :key="r.id" class="my-item" :class="{ 'is-cancelled': r.status === 3 }">
            <div class="my-item__top">
              <b>车位 #{{ r.spaceId }}</b>
              <el-tag size="small" :type="myStatusTag[r.status]" effect="light">{{ myStatusMap[r.status] }}</el-tag>
            </div>
            <div class="my-item__time">
              <span>{{ shortTime(r.startTime) }}</span>
              <span class="my-item__arrow">→</span>
              <span>{{ shortTime(r.endTime) }}</span>
            </div>
            <div class="my-item__foot">
              <span class="hint">{{ r.status === 0 ? '请在时段开始后尽快入场，超时自动释放' : '—' }}</span>
              <el-button v-if="r.status === 0" size="small" type="danger" text @click="handleCancel(r)">取消预约</el-button>
            </div>
          </article>
        </div>
        <div v-else class="sp-empty">
          <span class="sp-empty__star">✦</span>
          <span class="sp-empty__text">还没有预约记录</span>
        </div>
      </section>
    </div>

    <!-- 预约弹窗 -->
    <el-dialog v-model="dialogVisible" title="预约车位" width="460px">
      <div class="dialog-space">
        <span class="sp-plate">{{ currentRow?.spaceNo }}</span>
        <div>
          <b>{{ currentRow?.area }} 区 · {{ currentRow?.floor || '—' }}</b>
          <i>{{ typeMap[currentRow?.type] }} · {{ currentRow?.monthPrice ? '¥' + currentRow.monthPrice + '/月' : '临停计费' }}</i>
        </div>
      </div>

      <el-form label-width="84px" class="reserve-form">
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" style="width: 100%" :disabled-date="disabledDate" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" style="width: 100%" :disabled-date="disabledDate" />
        </el-form-item>
        <el-form-item label="快捷时长">
          <div class="quick-dur">
            <el-button v-for="h in [1, 2]" :key="h" size="small" @click="setDuration(h)">{{ h }} 小时</el-button>
          </div>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon>
        单次预约最长 2 小时，锁定后请按时入场；超时未入场车位会自动释放给其他业主。
      </el-alert>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReserve">确认预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getAllSpaces, getAvailableSpaces, reserveSpace, cancelReserve, myReservations } from '@/api/parking'
import { getUserId } from '@/api/user'

const loading = ref(false)
const myLoading = ref(false)
const submitting = ref(false)
const area = ref('')
const onlyFree = ref(false)
const tableData = ref([])
const myList = ref([])
const dialogVisible = ref(false)
const currentRow = ref(null)
const form = reactive({ startTime: '', endTime: '' })

/** 区域由真实车位数据派生，避免写死 A/B/C 丢掉 D 区（E2E 报告 P1-7） */
const areaOptions = ref([])
const areas = computed(() => (areaOptions.value.length ? areaOptions.value : ['A', 'B', 'C', 'D']))

const mergeAreas = (list) => {
  const set = new Set(areaOptions.value)
  list.forEach((s) => {
    if (s && s.area) set.add(String(s.area))
  })
  areaOptions.value = [...set].sort()
}

const typeMap = { 0: '固定车位', 1: '临时车位', 2: '访客车位' }
const statusMap = { 0: '空闲', 1: '已占用', 2: '预约中', 3: '维护中' }
const myStatusMap = { 0: '预约中', 1: '已入场', 2: '已过期', 3: '已取消' }
const myStatusTag = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }

const counts = computed(() => {
  const c = { free: 0, used: 0, booked: 0, fix: 0 }
  tableData.value.forEach(s => {
    if (s.status === 0) c.free++
    else if (s.status === 1) c.used++
    else if (s.status === 2) c.booked++
    else if (s.status === 3) c.fix++
  })
  return c
})

const areaGroups = computed(() => {
  const map = {}
  tableData.value.forEach(s => {
    const a = s.area || 'A'
    map[a] = map[a] || []
    map[a].push(s)
  })
  return Object.keys(map).sort().map(a => ({
    area: a,
    list: map[a].slice().sort((x, y) => String(x.spaceNo).localeCompare(String(y.spaceNo))),
    free: map[a].filter(s => s.status === 0).length
  }))
})

const myActive = computed(() => myList.value.filter(r => r.status === 0))

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '—')

onMounted(() => reloadAll())

const reloadAll = () => {
  loadSpaces()
  loadMyReservations()
}

const loadSpaces = async () => {
  loading.value = true
  try {
    // 全部视图：/api/parking/all 返回 List（含占用/预约/维护）
    // 仅空闲：/api/parking/available 只返回 status=0 的车位，减少视觉噪声
    const res = onlyFree.value
      ? await getAvailableSpaces(area.value || undefined)
      : await getAllSpaces(area.value || undefined)
    tableData.value = res.data || []
    mergeAreas(tableData.value)
  } finally {
    loading.value = false
  }
}

const loadMyReservations = async () => {
  const userId = getUserId()
  if (!userId) return
  myLoading.value = true
  try {
    const res = await myReservations(userId)
    myList.value = res.data || []
  } finally {
    myLoading.value = false
  }
}

const disabledDate = (d) => d.getTime() < Date.now() - 86400000

const fmt = (d) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const openReserve = (row) => {
  if (!getUserId()) {
    ElMessage.warning('请先登录')
    return
  }
  currentRow.value = row
  // D1：预填时间向上取整到"下一分钟"。原来直接 setSeconds(0,0) 会把 11:21:42 截成 11:21:00，
  // 打开弹窗那一刻起就已经是过去时间，点确认必然被后端拒绝。
  const now = new Date()
  if (now.getSeconds() > 0 || now.getMilliseconds() > 0) {
    now.setMinutes(now.getMinutes() + 1)
  }
  now.setSeconds(0, 0)
  form.startTime = fmt(now)
  const end = new Date(now.getTime() + 3600000)
  form.endTime = fmt(end)
  dialogVisible.value = true
}

const setDuration = (h) => {
  const start = form.startTime ? new Date(String(form.startTime).replace(' ', 'T')) : new Date()
  form.startTime = fmt(start)
  form.endTime = fmt(new Date(start.getTime() + h * 3600000))
}

const submitReserve = async () => {
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择完整的预约时段')
    return
  }
  const start = new Date(String(form.startTime).replace(' ', 'T'))
  const end = new Date(String(form.endTime).replace(' ', 'T'))
  if (end <= start) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  if ((end - start) / 3600000 > 2) {
    ElMessage.warning('单次预约最多 2 小时')
    return
  }
  if (start < new Date(Date.now() - 60000)) {
    ElMessage.warning('开始时间不能早于当前时间')
    return
  }

  submitting.value = true
  try {
    // 后端 @RequestParam(spaceId, userId, startTime, endTime)，时间格式 yyyy-MM-dd HH:mm:ss
    await reserveSpace(currentRow.value.id, getUserId(), String(form.startTime), String(form.endTime))
    ElMessage.success('预约成功，请按时入场')
    dialogVisible.value = false
    reloadAll()
  } catch (e) {
    toastOnce(e, '预约失败')
  } finally {
    submitting.value = false
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm('取消后车位将立即释放，确定取消该预约？', '取消预约', {
      type: 'warning',
      confirmButtonText: '取消预约',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return
  }
  await cancelReserve(row.id, getUserId())
  ElMessage.success('已取消预约')
  reloadAll()
}
</script>

<style scoped>
.reserve-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--sp-4);
  align-items: start;
}

.lots {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  max-height: 620px;
  overflow-y: auto;
  padding-right: 4px;
}

.lot {
  border-radius: 14px;
  background: var(--sp-surface-2);
  border: 1px solid var(--sp-card-border);
  padding: 12px 14px 14px;
}

.lot__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.lot__name {
  font-size: 13px;
  font-weight: 800;
  color: var(--sp-text);
  letter-spacing: 0.06em;
}

.lot__meta {
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.lot__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 7px;
}

.cell {
  height: 56px;
  border: none;
  border-radius: 8px 8px 3px 3px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  color: #fff;
  cursor: default;
  transition: transform var(--sp-dur-micro) var(--sp-ease), box-shadow var(--sp-dur-micro) var(--sp-ease);
}

.cell b {
  font-size: 11px;
  font-weight: 800;
}

.cell i {
  font-style: normal;
  font-size: 9px;
  opacity: 0.82;
}

.cell.is-clickable {
  cursor: pointer;
}

.cell.is-clickable:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.2);
}

.cell--0 { background: linear-gradient(180deg, #46B08D, #2E9E7C); }
.cell--1 { background: linear-gradient(180deg, #93A6CB, #6B7FA8); opacity: 0.72; }
.cell--2 { background: linear-gradient(180deg, #DCAF5F, #C98F2F); opacity: 0.85; }
.cell--3 { background: linear-gradient(180deg, #D18787, #C25E5E); opacity: 0.72; }

/* 我的预约 */
.my-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.my-item {
  padding: 13px 14px;
  border-radius: 12px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  transition: border-color var(--sp-dur-micro) var(--sp-ease);
}

.my-item:hover {
  border-color: var(--sp-brand);
}

.my-item.is-cancelled {
  opacity: 0.62;
}

.my-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.my-item__top b {
  font-size: 13.5px;
  color: var(--sp-text);
}

.my-item__time {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--sp-text-2);
  font-variant-numeric: tabular-nums;
}

.my-item__arrow {
  color: var(--sp-text-3);
}

.my-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.hint {
  font-size: 11px;
  color: var(--sp-text-3);
}

/* 弹窗 */
.dialog-space {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  margin-bottom: 16px;
}

.dialog-space b {
  display: block;
  font-size: 13.5px;
  color: var(--sp-text);
}

.dialog-space i {
  font-style: normal;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.quick-dur {
  display: flex;
  gap: 8px;
}

.reserve-form {
  margin-bottom: 6px;
}

@media (max-width: 1100px) {
  .reserve-grid {
    grid-template-columns: 1fr;
  }
}
</style>
