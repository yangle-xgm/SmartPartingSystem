<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="IoT Devices"
      title="设备管理"
      desc="道闸、摄像头、地磁与显示屏统一纳管：设备心跳上报状态，摄像头识别触发 MQTT 联动入场 / 出场并自动抬杆。"
    >
      <template #actions>
        <el-button :loading="loading" @click="reload">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="success" @click="handleAdd">
          <el-icon><Plus /></el-icon>注册设备
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="设备总数" :value="overview.total" unit="台" icon="Monitor" tone="brand" hint="四类物联网设备" />
      <StatCard label="在线" :value="overview.online" unit="台" icon="CircleCheck" tone="teal" hint="心跳 60 秒内正常" />
      <StatCard label="离线" :value="overview.offline" unit="台" icon="SwitchButton" tone="warning" hint="需检查网络与供电" />
      <StatCard label="故障" :value="overview.fault" unit="台" icon="WarningFilled" tone="danger" hint="建议现场巡检" />
    </div>

    <div class="sp-toolbar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="设备类型">
          <el-select v-model="searchForm.deviceType" placeholder="全部" clearable style="width: 130px">
            <el-option label="道闸" :value="1" />
            <el-option label="摄像头" :value="2" />
            <el-option label="地磁" :value="3" />
            <el-option label="显示屏" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="离线" :value="0" />
            <el-option label="在线" :value="1" />
            <el-option label="故障" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="sp-toolbar__right">
        <el-button @click="openInvite">
          <el-icon><Promotion /></el-icon>邀请码入场
        </el-button>
        <el-button @click="handleMockCamera">
          <el-icon><Camera /></el-icon>模拟入场识别
        </el-button>
        <el-button @click="openExit">
          <el-icon><Van /></el-icon>出口放行
        </el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="设备编号" width="150">
          <template #default="{ row }">
            <span class="code">{{ row.deviceNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备名称" min-width="160" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.deviceType]" effect="light">{{ deviceTypeMap[row.deviceType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="安装位置" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 1 ? 'free' : row.status === 2 ? 'fix' : 'used'" />
              {{ statusMap[row.status] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后心跳" width="160">
          <template #default="{ row }">{{ shortTime(row.lastHeartbeat) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.deviceType === 1" size="small" text type="success" @click="handleGate(row, true)">开闸</el-button>
            <el-button v-if="row.deviceType === 1" size="small" text type="warning" @click="handleGate(row, false)">关闸</el-button>
            <el-button size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无设备，先注册一台摄像头试试</span>
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

    <!-- 注册 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '注册设备'" width="520px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备编号" prop="deviceNo">
              <el-input v-model="formData.deviceNo" placeholder="如 GATE-IN-01" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="formData.deviceName" placeholder="如 入口道闸" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备类型" prop="deviceType">
              <el-select v-model="formData.deviceType" style="width: 100%">
                <el-option label="道闸" :value="1" />
                <el-option label="摄像头" :value="2" />
                <el-option label="地磁" :value="3" />
                <el-option label="显示屏" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装位置" prop="location">
              <el-input v-model="formData.location" placeholder="如 北门入口" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="MQTT 主题">
          <el-input v-model="formData.mqttTopic" placeholder="如 device/gate/in/control" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 入场识别 -->
    <el-dialog v-model="cameraVisible" title="模拟摄像头识别 · 入场联动" width="500px">
      <el-form label-width="96px">
        <el-form-item label="摄像头">
          <el-select v-model="cameraForm.deviceNo" placeholder="选择摄像头" style="width: 100%">
            <el-option
              v-for="d in cameraDevices"
              :key="d.deviceNo"
              :label="`${d.deviceName}（${d.deviceNo}）`"
              :value="d.deviceNo"
            />
          </el-select>
          <p v-if="!cameraDevices.length" class="form-hint">暂无摄像头设备，请先注册类型为「摄像头」的设备</p>
        </el-form-item>
        <el-form-item label="车牌号">
          <el-input v-model="cameraForm.plateNo" placeholder="如 湘A12345" maxlength="10" />
        </el-form-item>
        <el-form-item label="识别置信度">
          <el-slider v-model="cameraForm.confidence" :min="50" :max="100" show-input />
          <p class="form-hint">≥ 80 精确识别自动入场；&lt; 80 触发模糊识别兜底，返回相似车牌待人工确认</p>
        </el-form-item>
      </el-form>

      <div v-if="cameraResult" class="result" :class="cameraResult.recognized ? 'is-ok' : 'is-warn'">
        <b>{{ cameraResult.message }}</b>
        <p v-if="cameraResult.orderNo">订单 {{ cameraResult.orderNo }} · 车位 {{ cameraResult.spaceNo }} · 道闸已开启</p>
      </div>

      <template #footer>
        <el-button @click="cameraVisible = false">关闭</el-button>
        <el-button type="primary" :loading="sending" @click="sendCamera">发送识别指令</el-button>
      </template>
    </el-dialog>

    <!-- 出口放行 -->
    <el-dialog v-model="exitVisible" title="出口放行 · 手动结算" width="480px">
      <el-form label-width="96px">
        <el-form-item label="道闸设备">
          <el-select v-model="exitForm.deviceNo" placeholder="选择出口道闸" style="width: 100%">
            <el-option
              v-for="d in gateDevices"
              :key="d.deviceNo"
              :label="`${d.deviceName}（${d.deviceNo}）`"
              :value="d.deviceNo"
            />
          </el-select>
          <p v-if="!gateDevices.length" class="form-hint">暂无道闸设备，请先注册类型为「道闸」的设备</p>
        </el-form-item>
        <el-form-item label="出场车牌">
          <el-input v-model="exitForm.plateNo" placeholder="如 湘A12345" maxlength="10" />
        </el-form-item>
      </el-form>
      <div v-if="exitResult" class="result" :class="exitResult.success === false ? 'is-warn' : 'is-ok'">
        <b>{{ exitResult.message || '出场结算完成，道闸已开启' }}</b>
        <p v-if="exitResult.amount != null">应付金额 ¥{{ exitResult.amount }}</p>
      </div>
      <template #footer>
        <el-button @click="exitVisible = false">关闭</el-button>
        <el-button type="primary" :loading="exitSending" @click="sendExit">确认放行</el-button>
      </template>
    </el-dialog>

    <!-- 访客凭码入场 -->
    <el-dialog v-model="inviteVisible" title="访客凭码入场" width="440px">
      <el-form label-width="80px">
        <el-form-item label="邀请码">
          <el-input v-model="inviteCode" placeholder="输入业主提供的 6 位核验码" maxlength="6" style="width: 220px" @keyup.enter="sendInvite" />
        </el-form-item>
      </el-form>
      <div v-if="inviteResult" class="result is-ok">
        <b>入场成功，道闸已开启</b>
        <p>订单 {{ inviteResult.orderNo }} · 车牌 {{ inviteResult.plateNo }} · 车位 {{ inviteResult.spaceNo }}</p>
      </div>
      <template #footer>
        <el-button @click="inviteVisible = false">关闭</el-button>
        <el-button type="primary" :loading="inviteSending" @click="sendInvite">核验入场</el-button>
      </template>
    </el-dialog>

    <!-- 设备详情 -->
    <el-drawer v-model="detailVisible" title="设备详情" size="420px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="device-hero">
            <span class="device-hero__icon" :data-type="detail.deviceType">
              <el-icon><Monitor /></el-icon>
            </span>
            <div>
              <b>{{ detail.deviceName }}</b>
              <span>{{ detail.deviceNo }}</span>
            </div>
            <el-tag :type="detail.status === 1 ? 'success' : detail.status === 2 ? 'danger' : 'info'" effect="light">
              {{ statusMap[detail.status] }}
            </el-tag>
          </div>

          <div class="sp-kv">
            <div class="sp-kv__item">
              <p class="sp-kv__k">设备类型</p>
              <p class="sp-kv__v">{{ deviceTypeMap[detail.deviceType] || '—' }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">安装位置</p>
              <p class="sp-kv__v small">{{ detail.location || '—' }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">最后心跳</p>
              <p class="sp-kv__v small">{{ shortTime(detail.lastHeartbeat) }}</p>
            </div>
            <div class="sp-kv__item">
              <p class="sp-kv__k">注册时间</p>
              <p class="sp-kv__v small">{{ shortTime(detail.createTime) }}</p>
            </div>
          </div>

          <div class="device-topic">
            <span>MQTT 主题</span>
            <code>{{ detail.mqttTopic || '未配置' }}</code>
          </div>

          <div class="device-actions">
            <el-button v-if="detail.deviceType === 1" type="success" @click="handleGate(detail, true)">开闸</el-button>
            <el-button v-if="detail.deviceType === 1" type="warning" plain @click="handleGate(detail, false)">关闸</el-button>
            <el-button @click="detailVisible = false; handleEdit(detail)">编辑设备</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getDeviceList,
  getDeviceDetail,
  getDeviceOverview,
  registerDevice,
  updateDevice,
  deleteDevice,
  openGate,
  closeGate,
  mockCamera,
  mockExitCamera,
  gateExit
} from '@/api/device'
import { inviteEntry } from '@/api/order'

const loading = ref(false)
const saving = ref(false)
const sending = ref(false)
const exitSending = ref(false)
const inviteSending = ref(false)

const tableData = ref([])
const overview = ref({})
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const cameraVisible = ref(false)
const cameraDevices = ref([])
const cameraForm = ref({ deviceNo: '', plateNo: '', confidence: 100 })
const cameraResult = ref(null)

const exitVisible = ref(false)
const gateDevices = ref([])
const exitForm = ref({ deviceNo: '', plateNo: '' })
const exitResult = ref(null)

const inviteVisible = ref(false)
const inviteCode = ref('')
const inviteResult = ref(null)

const deviceTypeMap = { 1: '道闸', 2: '摄像头', 3: '地磁', 4: '显示屏' }
const statusMap = { 0: '离线', 1: '在线', 2: '故障' }
const typeTag = { 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }

const searchForm = reactive({ deviceType: '', status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formData = ref({ deviceNo: '', deviceName: '', deviceType: 1, location: '', mqttTopic: '' })
const formRules = {
  deviceNo: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  location: [{ required: true, message: '请输入安装位置', trigger: 'blur' }]
}

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

onMounted(() => reload())

const reload = () => {
  loadData()
  loadOverview()
}

const loadOverview = async () => {
  try {
    const res = await getDeviceOverview()
    overview.value = res.data || {}
  } catch (e) {
    overview.value = {}
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (searchForm.deviceType !== '') params.deviceType = searchForm.deviceType
    if (searchForm.status !== '') params.status = searchForm.status
    const res = await getDeviceList(params)
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { Object.assign(searchForm, { deviceType: '', status: '' }); handleSearch() }

const handleAdd = () => {
  isEdit.value = false
  formData.value = { deviceNo: '', deviceName: '', deviceType: 1, location: '', mqttTopic: '' }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  formData.value = { ...row }
  dialogVisible.value = true
}

/**
 * 详情：先展示列表行，再拉单条最新数据（心跳/状态可能已变化）。
 * getDeviceDetail(id) → GET /api/device/{id}
 */
const openDetail = async (row) => {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getDeviceDetail(row.id)
    if (res.data) detail.value = res.data
  } catch (e) {
    /* 拉取失败保留列表数据 */
  } finally {
    detailLoading.value = false
  }
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateDevice(formData.value.id, formData.value)
        ElMessage.success('设备已更新')
      } else {
        await registerDevice(formData.value)
        ElMessage.success('设备已注册')
      }
      dialogVisible.value = false
      reload()
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除设备「${row.deviceName}」？删除后该设备不再纳管。`, '删除设备', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await deleteDevice(row.id)
  ElMessage.success('已删除')
  reload()
}

const handleGate = async (row, isOpen) => {
  try {
    await ElMessageBox.confirm(
      `确定${isOpen ? '开启' : '关闭'}道闸「${row.deviceNo}」？指令会通过 MQTT 下发。`,
      isOpen ? '开闸确认' : '关闸确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  if (isOpen) await openGate(row.deviceNo)
  else await closeGate(row.deviceNo)
  ElMessage.success(isOpen ? '道闸已开启' : '道闸已关闭')
}

/* ============ 入场识别 ============ */
const handleMockCamera = async () => {
  const res = await getDeviceList({ deviceType: 2, current: 1, size: 50 })
  cameraDevices.value = res.data?.records || []
  cameraForm.value = { deviceNo: cameraDevices.value[0]?.deviceNo || '', plateNo: '', confidence: 100 }
  cameraResult.value = null
  cameraVisible.value = true
}

const sendCamera = async () => {
  if (!cameraForm.value.deviceNo) return ElMessage.warning('请选择摄像头设备')
  if (!cameraForm.value.plateNo.trim()) return ElMessage.warning('请输入车牌号')
  sending.value = true
  cameraResult.value = null
  try {
    const res = await mockCamera(cameraForm.value.deviceNo, cameraForm.value.plateNo.trim(), cameraForm.value.confidence)
    cameraResult.value = res.data || {}
    if (res.data?.recognized) {
      ElMessage.success('联动成功：已自动入场并抬杆')
      reload()
    } else {
      ElMessage.warning(res.data?.message || '未自动入场，请检查车牌或车位')
    }
  } catch (e) {
    toastOnce(e, '联动失败')
  } finally {
    sending.value = false
  }
}

/* ============ 出口放行 ============ */
const openExit = async () => {
  const res = await getDeviceList({ deviceType: 1, current: 1, size: 50 })
  gateDevices.value = res.data?.records || []
  exitForm.value = { deviceNo: gateDevices.value[0]?.deviceNo || '', plateNo: '' }
  exitResult.value = null
  exitVisible.value = true
}

const sendExit = async () => {
  if (!exitForm.value.deviceNo) return ElMessage.warning('请选择出口道闸')
  if (!exitForm.value.plateNo.trim()) return ElMessage.warning('请输入出场车牌')
  exitSending.value = true
  exitResult.value = null
  try {
    const res = await gateExit(exitForm.value.deviceNo, exitForm.value.plateNo.trim())
    exitResult.value = res.data || {}
    ElMessage.success('出场结算完成')
    reload()
  } catch (e) {
    toastOnce(e, '放行失败')
  } finally {
    exitSending.value = false
  }
}

/* ============ 访客凭码入场 ============ */
const openInvite = () => {
  inviteCode.value = ''
  inviteResult.value = null
  inviteVisible.value = true
}

const sendInvite = async () => {
  if (!inviteCode.value.trim()) return ElMessage.warning('请输入邀请码')
  inviteSending.value = true
  try {
    const res = await inviteEntry(inviteCode.value.trim())
    inviteResult.value = res.data || {}
    ElMessage.success('核验通过，已放行')
    reload()
  } catch (e) {
    toastOnce(e, '核验失败')
  } finally {
    inviteSending.value = false
  }
}

// 供模板调用的"邀请码入场"入口（保留在工具条按钮中）
defineExpose({ openInvite })
</script>

<style scoped>
.code {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--sp-text);
  font-weight: 600;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.form-hint {
  font-size: 11.5px;
  color: var(--sp-text-3);
  line-height: 1.6;
  margin-top: 4px;
}

.result {
  margin-top: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface-2);
}

.result b {
  display: block;
  font-size: 13px;
  color: var(--sp-text);
}

.result p {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--sp-text-2);
}

.result.is-ok {
  border-color: var(--sp-teal);
  background: var(--sp-teal-soft);
}

.result.is-warn {
  border-color: var(--sp-warning);
  background: var(--sp-warning-soft);
}

/* 设备详情抽屉 */
.device-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  background: var(--sp-surface-2);
  margin-bottom: 14px;
}

.device-hero__icon {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 12px;
  font-size: 18px;
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
}

.device-hero__icon[data-type='1'] { color: var(--sp-warning); background: var(--sp-warning-soft); }
.device-hero__icon[data-type='3'] { color: var(--sp-teal); background: var(--sp-teal-soft); }
.device-hero__icon[data-type='4'] { color: var(--sp-gold); background: var(--sp-gold-soft); }

.device-hero > div {
  flex: 1;
  min-width: 0;
}

.device-hero b {
  display: block;
  font-size: 14px;
  color: var(--sp-text);
}

.device-hero span {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: var(--sp-text-3);
}

.device-topic {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px dashed var(--sp-card-border);
}

.device-topic span {
  display: block;
  font-size: 11px;
  color: var(--sp-text-3);
  margin-bottom: 4px;
}

.device-topic code {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--sp-text-2);
  word-break: break-all;
}

.device-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
</style>
