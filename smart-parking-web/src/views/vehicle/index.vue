<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Vehicles"
      :title="isOwner ? '我的车辆' : '车辆档案'"
      :desc="isOwner
        ? '绑定车牌后即可无感通行：摄像头识别到已登记车牌自动抬杆，月卡车按到期时间自动续期计费。'
        : '全量车辆档案与月卡状态。车牌号以 AES-256 加密入库，页面仅展示脱敏结果。'"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>绑定车辆
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard :label="isOwner ? '我的车辆' : '车辆总数'" :value="stats.total" unit="辆" icon="Van" tone="brand" />
      <StatCard label="正常通行" :value="stats.normal" unit="辆" icon="CircleCheck" tone="teal" hint="未被列入黑名单" />
      <StatCard label="月卡车" :value="stats.monthly" unit="辆" icon="Calendar" tone="gold" hint="按到期时间续费" />
      <StatCard label="黑名单" :value="stats.blocked" unit="辆" icon="WarningFilled" tone="danger" hint="禁止入场" />
    </div>

    <div class="sp-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索车牌，如 湘A****7（按脱敏号匹配）"
        clearable
        style="width: 220px"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="sp-toolbar__right">
        <el-radio-group v-model="filter" size="default">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="normal">正常</el-radio-button>
          <el-radio-button value="blocked">黑名单</el-radio-button>
          <el-radio-button value="monthly">月卡</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="filtered" v-loading="loading" stripe>
        <el-table-column label="车牌号" width="140">
          <template #default="{ row }">
            <span class="sp-plate" :data-color="row.plateColor">{{ row.plateNoMasked }}</span>
          </template>
        </el-table-column>
        <el-table-column label="颜色" width="90">
          <template #default="{ row }">{{ row.plateColor || '蓝' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.type === 0 ? 'primary' : 'warning'" effect="light">
              {{ row.type === 0 ? '私家车' : '访客车辆' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="品牌 / 车身" min-width="140">
          <template #default="{ row }">
            {{ row.brand || '—' }}<span class="muted"> / {{ row.color || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isOwner" label="业主 ID" width="110">
          <template #default="{ row }">{{ row.ownerId || '—' }}</template>
        </el-table-column>
        <el-table-column label="月卡到期" width="160">
          <template #default="{ row }">
            <span v-if="row.monthlyExpire" class="monthly" :class="{ 'is-expiring': isExpiring(row.monthlyExpire) }">
              {{ shortDate(row.monthlyExpire) }}
              <i v-if="isExpiring(row.monthlyExpire)">即将到期</i>
            </span>
            <span v-else class="muted">非月卡</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 0 ? 'free' : 'fix'" />
              {{ row.status === 0 ? '正常' : '黑名单' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.monthlyExpire" size="small" text type="primary" @click="handleRenew(row)">续费一月</el-button>
            <el-button
              v-if="!isOwner"
              size="small"
              text
              :type="row.status === 0 ? 'warning' : 'success'"
              @click="handleToggleBlacklist(row)"
            >{{ row.status === 0 ? '加黑名单' : '移出黑名单' }}</el-button>
            <el-button size="small" text type="danger" @click="handleUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">{{ isOwner ? '还没有绑定车牌，绑定后即可无感通行' : '暂无车辆记录' }}</span>
          </div>
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" title="绑定车辆" width="520px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="96px">
        <el-form-item label="车牌号" prop="plateNo">
          <div class="plate-row">
            <el-input v-model="formData.plateNo" placeholder="如 湘A12345" maxlength="10" />
            <el-button :loading="checking" @click="checkPlate">识别校验</el-button>
          </div>
          <p v-if="checkTip" class="plate-tip" :class="{ 'is-warn': checkWarn }">{{ checkTip }}</p>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="车牌颜色" prop="plateColor">
              <el-select v-model="formData.plateColor" style="width: 100%">
                <el-option label="蓝牌" value="蓝" />
                <el-option label="绿牌（新能源）" value="绿" />
                <el-option label="黄牌" value="黄" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车辆类型">
              <el-select v-model="formData.type" style="width: 100%" :disabled="isOwner">
                <el-option label="私家车" :value="0" />
                <el-option label="访客车辆" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="formData.brand" placeholder="如 比亚迪" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车身颜色">
              <el-input v-model="formData.color" placeholder="如 白色" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!isOwner" label="业主 ID" prop="ownerId">
          <el-input v-model="formData.ownerId" placeholder="归属业主的用户ID，用于订单与通知归属" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          新能源绿牌在出场计费时自动享受优惠规则；月卡车请绑定后联系物业开通月卡。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确认绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getVehicleList, bindVehicle, unbindVehicle, toggleBlacklist, renewMonthly, recognizePlate } from '@/api/vehicle'
import { getUserId, getRole } from '@/api/user'

const loading = ref(false)
const saving = ref(false)
const checking = ref(false)
const checkTip = ref('')
const checkWarn = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)
const keyword = ref('')
const filter = ref('all')

const isOwner = computed(() => getRole() === 0)

const formData = ref({ plateNo: '', plateColor: '蓝', brand: '', color: '', type: 0, ownerId: '' })
const formRules = {
  plateNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  plateColor: [{ required: true, message: '请选择车牌颜色', trigger: 'change' }],
  ownerId: [{ required: true, message: '请输入归属业主ID', trigger: 'blur' }]
}

const stats = computed(() => {
  const s = { total: tableData.value.length, normal: 0, monthly: 0, blocked: 0 }
  tableData.value.forEach(v => {
    if (v.status === 0) s.normal++
    else s.blocked++
    if (v.monthlyExpire) s.monthly++
  })
  return s
})

const filtered = computed(() => {
  let list = tableData.value
  if (filter.value === 'normal') list = list.filter(v => v.status === 0)
  else if (filter.value === 'blocked') list = list.filter(v => v.status !== 0)
  else if (filter.value === 'monthly') list = list.filter(v => !!v.monthlyExpire)
  const k = keyword.value.trim().toUpperCase()
  if (k) {
    // DEF-10：后端只回脱敏车牌，完整车牌搜不到。这里把 * 去掉再匹配，
    // 输入 `湘A7` 能命中 `湘A****7`；若接口带了明文 plateNo 也一并匹配。
    const stripMask = (s) => String(s || '').toUpperCase().replace(/\*/g, '')
    const kk = stripMask(k)
    list = list.filter(v =>
      stripMask(v.plateNoMasked).includes(kk) ||
      String(v.plateNo || '').toUpperCase().includes(k))
  }
  return list
})

const shortDate = (t) => (t ? String(t).replace('T', ' ').slice(0, 10) : '—')

// 30 天内到期视为"即将到期"
const isExpiring = (t) => {
  if (!t) return false
  const d = new Date(String(t).replace(' ', 'T')).getTime()
  return d - Date.now() < 30 * 86400000
}

const loadData = async () => {
  loading.value = true
  try {
    // GET /api/vehicle/list 返回 List<Vehicle>（非分页），userId 为空表示管理端看全部
    const res = await getVehicleList(isOwner.value ? getUserId() : null)
    tableData.value = Array.isArray(res.data) ? res.data : []
  } finally {
    loading.value = false
  }
}

// 必须放在 loadData 定义之后：onMounted 会立即求值参数，提前引用会命中 TDZ 抛错
onMounted(loadData)

const handleAdd = () => {
  checkTip.value = ''
  checkWarn.value = false
  formData.value = {
    plateNo: '',
    plateColor: '蓝',
    brand: '',
    color: '',
    type: 0,
    ownerId: isOwner.value ? String(getUserId() || '') : ''
  }
  dialogVisible.value = true
}

/**
 * 识别校验：绑卡前先跑一次车牌识别（POST /api/vehicle/recognize），避免重复绑定/黑名单车辆。
 * 后端语义：recognized=true 表示命中已登记且非黑名单车辆；message 有值时通常是黑名单拦截。
 */
const checkPlate = async () => {
  const plateNo = String(formData.value.plateNo || '').trim()
  if (!plateNo) {
    ElMessage.warning('请先输入车牌号')
    return
  }
  checking.value = true
  checkTip.value = ''
  checkWarn.value = false
  try {
    const res = await recognizePlate(plateNo, 100)
    const r = res.data || {}
    if (r.message) {
      checkWarn.value = true
      checkTip.value = r.message
    } else if (r.recognized && r.vehicleId) {
      checkWarn.value = true
      checkTip.value = `该车牌已登记（${r.plateNoMasked || plateNo}），请勿重复绑定`
    } else {
      checkTip.value = '该车牌尚未登记，可正常绑定'
      if (r.plateColor) formData.value.plateColor = r.plateColor
    }
  } catch (e) {
    checkWarn.value = true
    checkTip.value = e.message || '识别校验失败，请稍后重试'
  } finally {
    checking.value = false
  }
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await bindVehicle({
        plateNo: formData.value.plateNo.trim(),
        plateColor: formData.value.plateColor,
        brand: formData.value.brand,
        color: formData.value.color,
        type: formData.value.type,
        ownerId: isOwner.value ? getUserId() : Number(formData.value.ownerId)
      })
      ElMessage.success('车辆绑定成功')
      dialogVisible.value = false
      loadData()
    } catch (e) {
      toastOnce(e, '绑定失败')
    } finally {
      saving.value = false
    }
  })
}

const handleUnbind = async (row) => {
  try {
    await ElMessageBox.confirm(`解绑后该车牌将无法自动识别入场，确定解绑 ${row.plateNoMasked}？`, '解绑车辆', {
      type: 'warning',
      confirmButtonText: '解绑',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await unbindVehicle(row.id, getUserId())
  ElMessage.success('已解绑')
  loadData()
}

const handleToggleBlacklist = async (row) => {
  const next = row.status === 0 ? 1 : 0
  const action = next === 1 ? '加入黑名单' : '移出黑名单'
  try {
    await ElMessageBox.confirm(`确定将 ${row.plateNoMasked} ${action}？`, action, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await toggleBlacklist(row.id, next)
  ElMessage.success(`${action}成功`)
  loadData()
}

const handleRenew = async (row) => {
  try {
    await ElMessageBox.confirm(`确定为 ${row.plateNoMasked} 续费一个月月卡？`, '月卡续费', {
      type: 'info',
      confirmButtonText: '续费',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await renewMonthly(row.id)
  ElMessage.success('续费成功，月卡延长一个月')
  loadData()
}
</script>

<style scoped>
.muted {
  color: var(--sp-text-3);
  font-size: 12px;
}

.plate-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.plate-tip {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--sp-teal);
}

.plate-tip.is-warn {
  color: var(--sp-warning);
}

.monthly {
  font-size: 12.5px;
  color: var(--sp-text);
  font-variant-numeric: tabular-nums;
}

.monthly.is-expiring {
  color: var(--sp-warning);
  font-weight: 600;
}

.monthly i {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--sp-warning-soft);
  font-style: normal;
  font-size: 10px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}
</style>
