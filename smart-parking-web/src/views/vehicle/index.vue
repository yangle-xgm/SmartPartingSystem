<template>
  <div class="vehicle-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isOwner ? '我的车辆' : '车辆列表' }}</span>
          <div v-if="!isOwner">
            <el-button type="success" @click="handleAdd">绑定车辆</el-button>
          </div>
          <div v-else>
            <el-button type="success" @click="handleAdd">绑定车辆</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="plateNoMasked" label="车牌号" width="150" />
        <el-table-column prop="plateColor" label="车牌颜色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.plateColor === '绿' ? 'success' : ''">{{ row.plateColor }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="brand" label="品牌" width="120" />
        <el-table-column prop="color" label="车身颜色" width="100" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 0 ? '' : 'warning'">{{ row.type === 0 ? '私家车' : '访客车辆' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '黑名单' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="绑定时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleUnbind(row)">解绑</el-button>
            <el-button v-if="!isOwner" size="small" :type="row.status === 0 ? 'warning' : 'success'" @click="handleToggleBlacklist(row)">
              {{ row.status === 0 ? '加黑名单' : '移黑名单' }}
            </el-button>
            <el-button v-if="row.type === 2" size="small" type="primary" @click="handleRenew(row)">续费</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="绑定车辆" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model="formData.plateNo" placeholder="请输入车牌号，如：京A12345" />
        </el-form-item>
        <el-form-item label="车牌颜色" prop="plateColor">
          <el-select v-model="formData.plateColor" placeholder="请选择">
            <el-option label="蓝牌" value="蓝" />
            <el-option label="绿牌" value="绿" />
            <el-option label="黄牌" value="黄" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="formData.brand" placeholder="请输入车辆品牌" />
        </el-form-item>
        <el-form-item label="车身颜色">
          <el-input v-model="formData.color" placeholder="请输入车身颜色" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVehicleList, bindVehicle, unbindVehicle, toggleBlacklist, renewMonthly } from '@/api/vehicle'
import { getRole } from '@/api/user'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)
const formData = ref({ plateNo: '', plateColor: '蓝', brand: '', color: '' })
const formRules = {
  plateNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  plateColor: [{ required: true, message: '请选择车牌颜色', trigger: 'change' }]
}

// 业主视角：只看自己的车
const isOwner = computed(() => getRole() === 0)

const getUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.userId
  } catch { return null }
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const res = await getVehicleList(isOwner.value ? getUserId() : null)
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  formData.value = { plateNo: '', plateColor: '蓝', brand: '', color: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    await bindVehicle({ ...formData.value, ownerId: getUserId() })
    ElMessage.success('绑定成功')
    dialogVisible.value = false
    loadData()
  })
}

const handleUnbind = async (row) => {
  await ElMessageBox.confirm(`确定要解绑车辆 ${row.plateNoMasked} 吗？`, '提示', { type: 'warning' })
  await unbindVehicle(row.id, getUserId())
  ElMessage.success('解绑成功')
  loadData()
}

const handleToggleBlacklist = async (row) => {
  const newStatus = row.status === 0 ? 1 : 0
  const action = newStatus === 1 ? '加入黑名单' : '移出黑名单'
  await ElMessageBox.confirm(`确定要将 ${row.plateNoMasked} ${action}吗？`, '提示', { type: 'warning' })
  await toggleBlacklist(row.id, newStatus)
  ElMessage.success(`${action}成功`)
  loadData()
}

const handleRenew = async (row) => {
  await ElMessageBox.confirm(`确定为 ${row.plateNoMasked} 续费一个月月卡吗？`, '月卡续费', { type: 'info' })
  await renewMonthly(row.id)
  ElMessage.success('续费成功，月卡延长一个月')
  loadData()
}
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
