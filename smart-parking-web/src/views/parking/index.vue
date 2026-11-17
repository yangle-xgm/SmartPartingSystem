<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Parking Asset"
      title="车位管理"
      desc="维护车位档案与实时状态。车位状态由入场、出场、预约事件驱动（Redis 实时 + 数据库落库），支持按小区与区域隔离。"
    >
      <template #actions>
        <el-radio-group v-model="viewMode" size="default">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="map">车位地图</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增车位
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="车位总数" :value="summary.total" unit="个" icon="Location" tone="brand" :hint="filterActive ? '当前筛选条件下' : '当前小区可见范围'" />
      <StatCard label="空闲可停" :value="summary.free" unit="个" icon="CircleCheck" tone="teal" hint="可直接入场或预约" />
      <StatCard label="已占用" :value="summary.used" unit="个" icon="Van" tone="warning" hint="含月卡与临停车辆" />
      <StatCard label="预约中" :value="summary.booked" unit="个" icon="Clock" tone="gold" hint="超时未入场自动释放" />
      <StatCard label="维护中" :value="summary.fix" unit="个" icon="Tools" tone="danger" hint="暂停对外分配" />
    </div>

    <div class="sp-toolbar">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="区域">
          <el-select v-model="searchForm.area" placeholder="全部区域" clearable style="width: 130px">
            <el-option v-for="a in areas" :key="a" :label="a + ' 区'" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="固定车位" :value="0" />
            <el-option label="临时车位" :value="1" />
            <el-option label="访客车位" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="空闲" :value="0" />
            <el-option label="已占用" :value="1" />
            <el-option label="预约中" :value="2" />
            <el-option label="维护中" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="sp-toolbar__right">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 车位地图 -->
    <section v-if="viewMode === 'map'" class="sp-card">
      <div class="sp-card__head">
        <span class="sp-card__title">车位实时地图 <span class="sp-card__sub">点击车位查看详情</span></span>
        <div class="sp-legend">
          <span class="sp-legend__item"><i class="sp-dot" data-tone="free" />空闲</span>
          <span class="sp-legend__item"><i class="sp-dot" data-tone="used" />占用</span>
          <span class="sp-legend__item"><i class="sp-dot" data-tone="book" />预约</span>
          <span class="sp-legend__item"><i class="sp-dot" data-tone="fix" />维护</span>
        </div>
      </div>

      <div v-if="areaGroups.length" class="lot-wrap">
        <div v-for="g in areaGroups" :key="g.area" class="lot">
          <div class="lot__head">
            <span class="lot__name">{{ g.area }} 区</span>
            <span class="lot__meta">{{ g.list.length }} 个车位 · 空闲 {{ g.free }}</span>
          </div>
          <div class="lot__lane">入口 ↓</div>
          <div class="lot__grid">
            <button
              v-for="s in g.list"
              :key="s.id"
              class="cell"
              :class="'cell--' + s.status"
              :title="`${s.spaceNo} · ${statusMap[s.status]}`"
              @click="openDetail(s)"
            >
              <b>{{ s.spaceNo }}</b>
              <i>{{ typeMap[s.type] }}</i>
            </button>
          </div>
          <div class="lot__lane">↑ 出口</div>
        </div>
      </div>
      <div v-else class="sp-empty">
        <span class="sp-empty__star">✦</span>
        <span class="sp-empty__text">该筛选条件下暂无车位</span>
      </div>
    </section>

    <!-- 车位列表 -->
    <section v-else class="sp-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="车位号" width="120">
          <template #default="{ row }">
            <span class="space-no">{{ row.spaceNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="区域" width="90">
          <template #default="{ row }">{{ row.area }} 区</template>
        </el-table-column>
        <el-table-column prop="floor" label="楼层" width="90" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.type]" effect="light">{{ typeMap[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="statusDot[row.status]" />
              {{ statusMap[row.status] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="月租价格" width="110">
          <template #default="{ row }">
            <span class="sp-num">{{ row.monthPrice ? '¥' + row.monthPrice : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="共享" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isShared === 1" type="success" effect="light">共享中</el-tag>
            <span v-else class="muted">不共享</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerId" label="业主ID" width="110">
          <template #default="{ row }">{{ row.ownerId || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无车位，先新增一个吧</span>
          </div>
        </template>
      </el-table>

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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑车位' : '新增车位'" width="520px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="96px">
        <el-form-item label="车位编号" prop="spaceNo">
          <el-input v-model="formData.spaceNo" placeholder="如 A-001" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="区域" prop="area">
              <el-select v-model="formData.area" placeholder="选择区域" style="width: 100%">
                <el-option v-for="a in areas" :key="a" :label="a + ' 区'" :value="a" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="楼层">
              <el-input v-model="formData.floor" placeholder="如 B1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="formData.type" placeholder="选择类型" style="width: 100%">
                <el-option label="固定车位" :value="0" />
                <el-option label="临时车位" :value="1" />
                <el-option label="访客车位" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="月租价格" prop="monthPrice">
              <el-input-number v-model="formData.monthPrice" :min="0" :precision="2" :step="50" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="0">空闲</el-radio>
            <el-radio :value="1">已占用</el-radio>
            <el-radio :value="2">预约中</el-radio>
            <el-radio :value="3">维护中</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="车位详情" size="420px">
      <div v-loading="detailLoading">
        <template v-if="current">
          <div class="detail-hero">
            <b>{{ current.spaceNo }}</b>
            <span class="status">
              <i class="sp-dot" :data-tone="statusDot[current.status]" />
              {{ statusMap[current.status] }}
            </span>
          </div>
          <div class="sp-kv detail-kv">
            <div class="sp-kv__item">
              <p class="sp-kv__k">所属区域</p>
              <p class="sp-kv__v">{{ current.area }} 区 · {{ current.floor || '—' }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">车位类型</p>
              <p class="sp-kv__v">{{ typeMap[current.type] }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">月租价格</p>
              <p class="sp-kv__v">{{ current.monthPrice ? '¥' + current.monthPrice : '—' }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">业主 ID</p>
              <p class="sp-kv__v">{{ current.ownerId || '未绑定' }}</p>
            </div>
          </div>
          <div class="detail-share">
            <div>
              <b>共享给临停</b>
              <p>业主闲置时开放车位，按规则与物业分润。</p>
            </div>
            <el-switch
              :model-value="current.isShared === 1"
              @change="(v) => handleShare(current, v)"
            />
          </div>
          <div class="detail-actions">
            <el-button @click="detailVisible = false; handleEdit(current)">编辑车位</el-button>
            <el-button type="danger" plain @click="detailVisible = false; handleDelete(current)">删除车位</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getSpaceList, getAllSpaces, getSpaceDetail, addSpace, updateSpace, deleteSpace, toggleSpaceShare } from '@/api/parking'

const loading = ref(false)
const saving = ref(false)
const detailLoading = ref(false)
const tableData = ref([])
const allSpaces = ref([])
const viewMode = ref('list')
const dialogVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const current = ref(null)

/**
 * 区域选项来自真实数据，不再写死 ['A','B','C']（E2E 报告 P1-7：D 区 20 个车位完全不可见）。
 * loadAll() 首次必然是不带区域的全量查询，用它把区域集合建起来；之后带筛选的查询只做并集，
 * 避免选了 A 区后下拉里只剩 A 区、无法再切回其它区域。
 */
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
const typeTagMap = { 0: 'primary', 1: 'success', 2: 'warning' }
const statusDot = { 0: 'free', 1: 'used', 2: 'book', 3: 'fix' }

const searchForm = reactive({ area: '', type: '', status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formData = ref({ spaceNo: '', area: '', floor: 'B1', type: 1, monthPrice: null, status: 0 })
const formRules = {
  spaceNo: [{ required: true, message: '请输入车位编号', trigger: 'blur' }],
  area: [{ required: true, message: '请选择区域', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  // DEF-04：负数不仅没意义，还会被数据库无符号列截断成空值（"新增成功但价格没了"）
  monthPrice: [
    {
      validator: (rule, value, callback) => {
        if (value === null || value === undefined || value === '') return callback()
        if (Number(value) < 0) return callback(new Error('月租价格不能为负数'))
        callback()
      },
      trigger: ['blur', 'change']
    }
  ]
}

/**
 * 地图与统计卡的数据源（D3）：
 * `/api/parking/all` 只支持按区域过滤，类型/状态在这里做二次过滤。
 * 历史实现直接用全量 allSpaces，导致「状态选预约中 + 查询」后地图与统计卡毫无变化，
 * 与列表视图的口径不一致，用户会以为筛选没生效。
 */
const filteredSpaces = computed(() => {
  const { type, status } = searchForm
  const matchType = type === '' || type === null || type === undefined
  const matchStatus = status === '' || status === null || status === undefined
  return allSpaces.value.filter(s =>
    (matchType || Number(s.type) === Number(type)) &&
    (matchStatus || Number(s.status) === Number(status))
  )
})

const filterActive = computed(() =>
  !!(searchForm.area || searchForm.type !== '' || searchForm.status !== '')
)

const summary = computed(() => {
  const c = { total: filteredSpaces.value.length, free: 0, used: 0, booked: 0, fix: 0 }
  filteredSpaces.value.forEach(s => {
    if (s.status === 0) c.free++
    else if (s.status === 1) c.used++
    else if (s.status === 2) c.booked++
    else if (s.status === 3) c.fix++
  })
  return c
})

const areaGroups = computed(() => {
  const map = {}
  filteredSpaces.value.forEach(s => {
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

onMounted(() => {
  loadData()
  loadAll()
})

const loadData = async () => {
  loading.value = true
  try {
    // 后端 ParkingSpaceQuery 只接受 area/type/status/current/size，空值不传
    const params = { current: pagination.current, size: pagination.size }
    if (searchForm.area) params.area = searchForm.area
    if (searchForm.type !== '') params.type = searchForm.type
    if (searchForm.status !== '') params.status = searchForm.status
    const res = await getSpaceList(params)
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// 概览与地图用全量数据（/api/parking/all 返回 List）
const loadAll = async () => {
  try {
    const res = await getAllSpaces(searchForm.area || undefined)
    allSpaces.value = res.data || []
    mergeAreas(allSpaces.value)
  } catch (e) {
    allSpaces.value = []
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadData()
  loadAll()
}

const resetSearch = () => {
  Object.assign(searchForm, { area: '', type: '', status: '' })
  handleSearch()
}

/**
 * 详情：先用列表行数据立刻打开抽屉，再拉最新单条覆盖。
 * getSpaceDetail(id) → GET /api/parking/space/{id}，保证共享开关、状态是最新值。
 */
const openDetail = async (row) => {
  current.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getSpaceDetail(row.id)
    if (res.data) current.value = res.data
  } catch (e) {
    /* 详情拉取失败时保留列表行数据，不打断查看 */
  } finally {
    detailLoading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  formData.value = { spaceNo: '', area: searchForm.area || 'A', floor: 'B1', type: 1, monthPrice: null, status: 0 }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  try {
    await form.validate()
  } catch (e) {
    return
  }
  // 兜底：即便输入框被绕过（粘贴、脚本赋值）也不把负数发给后端
  if (formData.value.monthPrice !== null && formData.value.monthPrice !== undefined
    && Number(formData.value.monthPrice) < 0) {
    ElMessage.warning('月租价格不能为负数')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateSpace(formData.value.id, formData.value)
      ElMessage.success('车位已更新')
    } else {
      await addSpace(formData.value)
      ElMessage.success('车位已新增')
    }
    dialogVisible.value = false
    loadData()
    loadAll()
  } catch (e) {
    // 错误提示由请求层统一弹出（编号重复/参数非法等），这里只保证不产生未捕获 Promise
    console.warn('[parking] 保存车位失败', e)
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`删除后车位 ${row.spaceNo} 将不再对外分配，确定继续？`, '删除车位', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteSpace(row.id)
    ElMessage.success('已删除')
    loadData()
    loadAll()
  } catch (e) {
    console.warn('[parking] 删除车位失败', e)
  }
}

const handleShare = async (row, val) => {
  const next = val ? 1 : 0
  try {
    await toggleSpaceShare(row.id, next)
    row.isShared = next
    // D9：抽屉里的 current 是独立对象，原来只改它，背后列表仍显示旧状态。
    // 按 id 回写到列表与地图数据源，保持三处一致（不重新拉全量，避免翻页/滚动位置丢失）。
    const patch = (list) => list.forEach(s => {
      if (String(s.id) === String(row.id)) s.isShared = next
    })
    patch(tableData.value)
    patch(allSpaces.value)
    if (current.value && String(current.value.id) === String(row.id)) current.value.isShared = next
    ElMessage.success(val ? '已开放共享' : '已关闭共享')
  } catch (e) {
    // 失败时回滚开关，避免界面与后端不一致（请求层已弹提示，这里不重复提示）
    row.isShared = val ? 0 : 1
    console.warn('[parking] 切换共享失败', e)
  }
}
</script>

<style scoped>
.space-no {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-weight: 700;
  color: var(--sp-text);
  letter-spacing: 0.04em;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.muted {
  font-size: 12.5px;
  color: var(--sp-text-3);
}

/* ---------------- 车位地图 ---------------- */
.lot-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.lot {
  border-radius: 14px;
  background: var(--sp-surface-2);
  border: 1px solid var(--sp-card-border);
  padding: 14px;
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

.lot__lane {
  text-align: center;
  font-size: 10.5px;
  letter-spacing: 0.3em;
  color: var(--sp-text-3);
  padding: 4px 0;
}

.lot__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(76px, 1fr));
  gap: 8px;
}

.cell {
  position: relative;
  height: 62px;
  border: none;
  border-radius: 8px 8px 3px 3px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #fff;
  transition: transform var(--sp-dur-micro) var(--sp-ease), box-shadow var(--sp-dur-micro) var(--sp-ease);
  overflow: hidden;
}

/* 车位地面标线 */
.cell::after {
  content: '';
  position: absolute;
  inset: auto 6px 5px 6px;
  height: 2px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.45);
}

.cell b {
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: 0.03em;
}

.cell i {
  font-style: normal;
  font-size: 9.5px;
  opacity: 0.8;
}

.cell:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.18);
}

.cell--0 { background: linear-gradient(180deg, #46B08D, #2E9E7C); }
.cell--1 { background: linear-gradient(180deg, #6B89C4, #4C6FA8); }
.cell--2 { background: linear-gradient(180deg, #DCAF5F, #C98F2F); }
.cell--3 { background: linear-gradient(180deg, #D18787, #C25E5E); }

/* ---------------- 详情 ---------------- */
.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px;
  border-radius: 14px;
  background: var(--sp-surface-2);
  margin-bottom: 14px;
}

.detail-hero b {
  font-size: 22px;
  font-weight: 800;
  color: var(--sp-text);
  letter-spacing: 0.04em;
}

.detail-kv {
  margin-bottom: 14px;
}

.detail-share {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: 12px;
  border: 1px dashed var(--sp-card-border);
  margin-bottom: 18px;
}

.detail-share b {
  font-size: 13px;
  color: var(--sp-text);
}

.detail-share p {
  font-size: 11.5px;
  color: var(--sp-text-3);
  margin-top: 3px;
  line-height: 1.5;
}

.detail-actions {
  display: flex;
  gap: 10px;
}
</style>
