<template>
  <div class="device-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="设备类型">
          <el-select v-model="searchForm.deviceType" placeholder="全部" clearable>
            <el-option label="道闸" :value="1" />
            <el-option label="摄像头" :value="2" />
            <el-option label="地磁" :value="3" />
            <el-option label="显示屏" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="离线" :value="0" />
            <el-option label="在线" :value="1" />
            <el-option label="故障" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="warning" plain @click="handleMockCamera">模拟摄像头识别</el-button>
          <el-button type="primary" plain @click="handleInviteEntryOpen">邀请码入场</el-button>
          <el-button type="success" @click="handleAdd">注册设备</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="deviceNo" label="设备编号" width="150" />
        <el-table-column prop="deviceName" label="设备名称" width="180" />
        <el-table-column prop="deviceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ deviceTypeMap[row.deviceType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="安装位置" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.deviceType === 1" size="small" type="success" @click="handleOpenGate(row)">开闸</el-button>
            <el-button v-if="row.deviceType === 1" size="small" type="warning" @click="handleCloseGate(row)">关闸</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="设备编号" prop="deviceNo">
          <el-input v-model="formData.deviceNo" placeholder="请输入设备编号" />
        </el-form-item>
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="formData.deviceName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceType">
          <el-select v-model="formData.deviceType" placeholder="请选择">
            <el-option label="道闸" :value="1" />
            <el-option label="摄像头" :value="2" />
            <el-option label="地磁" :value="3" />
            <el-option label="显示屏" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="安装位置" prop="location">
          <el-input v-model="formData.location" placeholder="请输入安装位置" />
        </el-form-item>
        <el-form-item label="MQTT主题">
          <el-input v-model="formData.mqttTopic" placeholder="如：device/gate/xxx/control" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 模拟摄像头识别弹窗 -->
    <el-dialog v-model="cameraVisible" title="模拟摄像头识别（MQTT 联动演示）" width="480px">
      <el-form label-width="100px">
        <el-form-item label="摄像头设备">
          <el-select v-model="cameraForm.deviceNo" placeholder="请选择摄像头" style="width: 100%">
            <el-option
              v-for="d in cameraDevices"
              :key="d.deviceNo"
              :label="`${d.deviceName}（${d.deviceNo}）`"
              :value="d.deviceNo"
            />
          </el-select>
          <div v-if="!cameraDevices.length" style="font-size: 12px; color: #e6a23c;">
            暂无摄像头设备，请先注册一个摄像头（类型选"摄像头"）
          </div>
        </el-form-item>
        <el-form-item label="车牌号">
          <el-input v-model="cameraForm.plateNo" placeholder="如：京A11111" maxlength="10" />
        </el-form-item>
        <el-form-item label="识别置信度">
          <el-slider v-model="cameraForm.confidence" :min="50" :max="100" show-input />
          <div style="font-size: 12px; color: #909399; line-height: 1.6;">
            置信度 ≥ 80：精确识别自动入场；&lt; 80：触发模糊识别兜底（返回相似车牌）
          </div>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="cameraResult"
        :type="cameraResult.recognized ? 'success' : 'warning'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px;"
      >
        <div class="result-line">
          {{ cameraResult.message }}<br v-if="cameraResult.orderNo" />
          <template v-if="cameraResult.orderNo">
            订单：{{ cameraResult.orderNo }} ｜ 车位：{{ cameraResult.spaceNo }}
          </template>
        </div>
      </el-alert>
      <template #footer>
        <el-button @click="cameraVisible = false">关闭</el-button>
        <el-button type="primary" :loading="cameraSending" @click="handleCameraSend">发送识别指令</el-button>
      </template>
    </el-dialog>

    <!-- 邀请码入场弹窗 -->
    <el-dialog v-model="inviteEntryVisible" title="访客凭码入场（模拟道闸核验）" width="440px">
      <el-form label-width="90px">
        <el-form-item label="邀请码">
          <el-input
            v-model="inviteCode"
            placeholder="请输入业主提供的6位邀请码"
            style="width: 220px"
            maxlength="6"
            @keyup.enter="handleInviteEntry"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="inviteEntryResult"
        type="success"
        :closable="false"
        show-icon
        title="入场成功，道闸已开启（模拟）"
      >
        <div class="result-line">
          订单号：{{ inviteEntryResult.orderNo }} ｜ 车牌：{{ inviteEntryResult.plateNo }} ｜ 车位：{{ inviteEntryResult.spaceNo }}
        </div>
      </el-alert>
      <template #footer>
        <el-button @click="inviteEntryVisible = false">关闭</el-button>
        <el-button type="primary" :loading="inviteEntering" @click="handleInviteEntry">核验入场</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeviceList, registerDevice, updateDevice, deleteDevice, openGate, closeGate, mockCamera } from '@/api/device'
import { inviteEntry } from '@/api/order'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const isEdit = ref(false)

// 模拟摄像头识别
const cameraVisible = ref(false)
const cameraSending = ref(false)
const cameraDevices = ref([])
const cameraForm = ref({ deviceNo: '', plateNo: '', confidence: 100 })
const cameraResult = ref(null)

// 邀请码入场
const inviteEntryVisible = ref(false)
const inviteEntering = ref(false)
const inviteCode = ref('')
const inviteEntryResult = ref(null)

const deviceTypeMap = { 1: '道闸', 2: '摄像头', 3: '地磁', 4: '显示屏' }
const statusMap = { 0: '离线', 1: '在线', 2: '故障' }
const statusTagMap = { 0: 'info', 1: 'success', 2: 'danger' }

const searchForm = reactive({ deviceType: '', status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formData = ref({ deviceNo: '', deviceName: '', deviceType: 1, location: '', mqttTopic: '' })
const formRules = {
  deviceNo: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  location: [{ required: true, message: '请输入安装位置', trigger: 'blur' }]
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const res = await getDeviceList({
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { Object.assign(searchForm, { deviceType: '', status: '' }); handleSearch() }

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '注册设备'
  formData.value = { deviceNo: '', deviceName: '', deviceType: 1, location: '', mqttTopic: '' }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑设备'
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    if (isEdit.value) {
      await updateDevice(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await registerDevice(formData.value)
      ElMessage.success('注册成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

const handleOpenGate = async (row) => {
  await ElMessageBox.confirm(`确定要开启道闸 ${row.deviceNo} 吗？`, '提示', { type: 'warning' })
  await openGate(row.deviceNo)
  ElMessage.success('道闸已开启')
}

const handleCloseGate = async (row) => {
  await ElMessageBox.confirm(`确定要关闭道闸 ${row.deviceNo} 吗？`, '提示', { type: 'warning' })
  await closeGate(row.deviceNo)
  ElMessage.success('道闸已关闭')
}

// ===== 邀请码入场 =====

const handleInviteEntryOpen = () => {
  inviteCode.value = ''
  inviteEntryResult.value = null
  inviteEntryVisible.value = true
}

const handleInviteEntry = async () => {
  if (!inviteCode.value.trim()) {
    ElMessage.warning('请输入邀请码')
    return
  }
  inviteEntering.value = true
  try {
    const res = await inviteEntry(inviteCode.value.trim())
    inviteEntryResult.value = res.data
    ElMessage.success(`入场成功，车位 ${res.data.spaceNo}`)
  } catch (e) {
    ElMessage.error(e.message || '核验失败')
  } finally {
    inviteEntering.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定要删除设备 ${row.deviceName} 吗？`, '提示', { type: 'warning' })
  await deleteDevice(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// ===== 模拟摄像头识别（MQTT联动） =====

const handleMockCamera = async () => {
  // 加载摄像头设备列表
  const res = await getDeviceList({ deviceType: 2, current: 1, size: 50 })
  cameraDevices.value = res.data.records || []
  cameraForm.value = { deviceNo: cameraDevices.value[0]?.deviceNo || '', plateNo: '', confidence: 100 }
  cameraResult.value = null
  cameraVisible.value = true
}

const handleCameraSend = async () => {
  if (!cameraForm.value.deviceNo) {
    ElMessage.warning('请选择摄像头设备')
    return
  }
  if (!cameraForm.value.plateNo.trim()) {
    ElMessage.warning('请输入车牌号')
    return
  }
  cameraSending.value = true
  cameraResult.value = null
  try {
    // 发布MQTT识别消息并等待联动结果（识别→自动入场→道闸开启）
    const res = await mockCamera(cameraForm.value.deviceNo, cameraForm.value.plateNo.trim(), cameraForm.value.confidence)
    cameraResult.value = res.data
    if (res.data.recognized && res.data.orderNo) {
      ElMessage.success(`联动成功：${res.data.plateNo} 已入场，车位 ${res.data.spaceNo}，道闸已开启`)
      loadData()
    } else {
      ElMessage.warning(res.data.message || '未自动入场')
    }
  } catch (e) {
    ElMessage.error(e.message || '联动失败')
  } finally {
    cameraSending.value = false
  }
}
</script>

<style lang="scss" scoped>
.search-card { margin-bottom: 16px; }
</style>
