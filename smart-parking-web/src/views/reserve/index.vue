<template>
  <div class="reserve-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true">
        <el-form-item label="车位区域">
          <el-select v-model="area" placeholder="全部区域" clearable style="width: 180px">
            <el-option label="A区" value="A" />
            <el-option label="B区" value="B" />
            <el-option label="C区" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSpaces">查询</el-button>
          <el-button @click="area = ''; loadSpaces()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>车位实时地图</span>
              <el-radio-group v-model="viewMode" size="small">
                <el-radio-button value="map">地图</el-radio-button>
                <el-radio-button value="list">列表</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-if="viewMode === 'map'" class="map-wrap">
            <div class="map-stats">
              <span class="stat-item"><i class="dot st0"></i>空闲 {{ freeCount }}</span>
              <span class="stat-item"><i class="dot st1"></i>占用 {{ usedCount }}</span>
              <span class="stat-item"><i class="dot st2"></i>预约 {{ reservedCount }}</span>
              <span class="stat-total">共 {{ tableData.length }} 个车位</span>
            </div>
            <div v-for="area in areaGroups" :key="area.name" class="area-section">
              <div class="area-label">{{ area.name }}区停车场</div>
              <div class="parking-lot">
                <div class="lane-top"><span class="lane-text">↑ 入口</span></div>
                <div class="organic-grid">
                  <div v-for="(space, idx) in area.list" :key="space.id"
                       class="parking-space" :class="[miniClass(space.status), spaceSize(idx)]"
                       @click="space.status === 0 ? openReserve(space) : null"
                       :title="`${space.spaceNo} | ${statusMap[space.status]}`">
                    <span class="space-label">{{ space.spaceNo }}</span>
                  </div>
                </div>
                <div class="lane-bottom"><span class="lane-text">↓ 出口</span></div>
              </div>
            </div>
          </div>
          <el-table v-else :data="tableData" v-loading="loading" border stripe height="480">
            <el-table-column prop="spaceNo" label="车位号" width="100" />
            <el-table-column prop="area" label="区域" width="80">
              <template #default="{ row }"><el-tag>{{ row.area }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagMap[row.status]">{{ statusMap[row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.status !== 0" @click="openReserve(row)">预约</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>我的预约</span>
            <el-button size="small" style="float:right" @click="loadMyReservations">刷新</el-button>
          </template>
          <el-table :data="myList" border stripe v-loading="myLoading" height="420">
            <el-table-column prop="spaceId" label="车位ID" width="80" />
            <el-table-column prop="startTime" label="开始时间" />
            <el-table-column prop="endTime" label="结束时间" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="myStatusTag[row.status]">{{ myStatusMap[row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" size="small" type="danger" @click="handleCancel(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预约时间段弹窗 -->
    <el-dialog v-model="dialogVisible" title="选择预约时段" width="420px">
      <el-form label-width="80px">
        <el-form-item label="车位">
          <span>{{ currentRow?.area }} - {{ currentRow?.spaceNo }}</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" style="width:100%" />
        </el-form-item>
        <el-alert type="info" :closable="false">单次预约最多2小时，超时未入场自动释放</el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReserve">确认预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllSpaces, reserveSpace, cancelReserve, myReservations } from '@/api/parking'
import { getUserId } from '@/api/user'

const loading = ref(false)
const area = ref('')
const tableData = ref([])
const viewMode = ref('map')
const dialogVisible = ref(false)
const submitting = ref(false)
const currentRow = ref(null)
const form = reactive({ startTime: '', endTime: '' })
const myList = ref([])
const myLoading = ref(false)

const myStatusMap = { 0: '已预约', 1: '已入场', 2: '已过期', 3: '已取消' }
const myStatusTag = { 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' }
const statusMap = { 0: '空闲', 1: '已占用', 2: '已预约' }
const statusTagMap = { 0: 'success', 1: 'danger', 2: 'warning' }

const miniClass = (status) => ['st0', 'st1', 'st2'][status] || 'st0'

const freeCount = computed(() => tableData.value.filter(s => s.status === 0).length)
const usedCount = computed(() => tableData.value.filter(s => s.status === 1).length)
const reservedCount = computed(() => tableData.value.filter(s => s.status === 2).length)
const areaGroups = computed(() => {
  const map = {}
  tableData.value.forEach(s => {
    const a = s.area || 'A'
    if (!map[a]) map[a] = []
    map[a].push(s)
  })
  return Object.keys(map).sort().map(k => ({ name: k, list: map[k] }))
})

const chunkedRows = (list) => {
  const perRow = 6
  const rows = []
  for (let i = 0; i < list.length; i += perRow * 2) {
    const left = list.slice(i, i + perRow)
    const right = list.slice(i + perRow, i + perRow * 2)
    rows.push({ key: i, left, right })
  }
  return rows
}

const spaceSize = (idx) => {
  const r = idx % 7
  if (r === 0) return 'wide'
  if (r === 3) return 'tall'
  return 'normal'
}

onMounted(() => { loadSpaces(); loadMyReservations() })

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

const handleCancel = async (row) => {
  await ElMessageBox.confirm('确定取消该预约吗？车位将立即释放', '取消预约', { type: 'warning' })
  const userId = getUserId()
  await cancelReserve(row.id, userId)
  ElMessage.success('已取消预约')
  loadMyReservations()
  loadSpaces()
}

const loadSpaces = async () => {
  loading.value = true
  try {
    const res = await getAllSpaces(area.value)
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

const openReserve = (row) => {
  const userId = getUserId()
  if (!userId) { ElMessage.warning('请先登录'); return }
  currentRow.value = row
  form.startTime = ''
  form.endTime = ''
  dialogVisible.value = true
}

const submitReserve = async () => {
  if (!form.startTime || !form.endTime) { ElMessage.warning('请选择完整时间段'); return }
  if (new Date(form.endTime) <= new Date(form.startTime)) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  const hours = (new Date(form.endTime) - new Date(form.startTime)) / 3600000
  if (hours > 2) { ElMessage.warning('单次预约最多2小时'); return }
  if (new Date(form.startTime) < new Date()) { ElMessage.warning('开始时间不能早于当前时间'); return }

  submitting.value = true
  try {
    const userId = getUserId()
    const pad = (n) => String(n).padStart(2, '0')
    const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    const start = fmt(new Date(form.startTime))
    const end = fmt(new Date(form.endTime))
    await reserveSpace(currentRow.value.id, userId, start, end)
    ElMessage.success('预约成功')
    dialogVisible.value = false
    loadSpaces()
  } catch (e) {
    ElMessage.error(e.message || '预约失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.search-card { margin-bottom: 16px; }
.map-wrap { padding: 10px; max-height: 600px; overflow-y: auto; }
.map-stats {
  display: flex; gap: 20px; padding: 10px 14px;
  background: #f5f7fa; border-radius: 8px; margin-bottom: 16px; font-size: 13px; align-items: center;
}
.stat-item { display: flex; align-items: center; gap: 6px; color: #333; }
.stat-total { margin-left: auto; color: #999; }
.dot {
  width: 10px; height: 10px; border-radius: 50%; display: inline-block;
  &.st0 { background: #67c23a; }
  &.st1 { background: #409eff; }
  &.st2 { background: #e6a23c; }
}
.area-section { margin-bottom: 20px; }
.area-label {
  font-size: 14px; font-weight: 600; color: #333; margin-bottom: 10px;
  padding-left: 10px; border-left: 3px solid #409eff;
}
.parking-lot {
  background: #e8e8e8;
  border-radius: 10px;
  padding: 12px;
}
.lane-top, .lane-bottom {
  background: #5a6268;
  color: #fff;
  text-align: center;
  padding: 6px;
  border-radius: 4px;
  font-size: 12px;
  margin: 6px 0;
}
.lane-text { opacity: .9; }
.organic-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  grid-auto-rows: 50px;
  gap: 6px;
  padding: 8px;
}
.parking-space {
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 6px;
  color: #fff;
  transition: all .2s;
  border: 2px solid rgba(255,255,255,.3);
  &.normal { grid-column: span 1; grid-row: span 1; }
  &.wide { grid-column: span 2; grid-row: span 1; }
  &.tall { grid-column: span 1; grid-row: span 2; }
  &:hover { transform: translateY(-3px); box-shadow: 0 4px 10px rgba(0,0,0,.2); }
}
.space-label { font-size: 12px; font-weight: 700; }
.st0 { background: linear-gradient(180deg, #67c23a, #529b2e); cursor: pointer; }
.st1 { background: linear-gradient(180deg, #409eff, #337ecc); }
.st2 { background: linear-gradient(180deg, #e6a23c, #b88230); }
</style>
