<template>
  <div class="invite-page">
    <el-card shadow="never" class="gen-card">
      <template #header>
        <div class="card-header">
          <span>添加访客车辆（白名单）</span>
        </div>
      </template>
      <el-form :inline="true" :model="genForm">
        <el-form-item label="访客车牌">
          <el-input v-model="genForm.plateNo" placeholder="如：京E88888" style="width: 180px" maxlength="10" />
        </el-form-item>
        <el-form-item label="有效期(小时)">
          <el-input-number v-model="genForm.expireHours" :min="1" :max="168" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="generating" @click="handleCreate">添加访客车辆</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon style="margin-top: 4px;">
        访客车辆入场时摄像头将自动识别车牌并放行，无需访客操作；有效期内可多次进出，到期自动失效。未登记车牌的访客可凭下方邀请码人工核验入场。
      </el-alert>

      <el-alert
        v-if="newInvite"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 8px;"
      >
        <div class="invite-result">
          已添加访客车 {{ newInvite.plateNoMasked }}，{{ newInvite.expireTime }} 前自动识别放行
          <el-button size="small" type="primary" plain @click="copyCode(newInvite.inviteCode)">
            复制核验码（{{ newInvite.inviteCode }}）
          </el-button>
        </div>
      </el-alert>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的访客车辆（白名单）</span>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="inviteCode" label="核验码" width="120">
          <template #default="{ row }">
            <b>{{ row.inviteCode }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="plateNoMasked" label="访客车牌" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireTime" label="到期时间" width="180" />
        <el-table-column prop="usedTime" label="最近入场" width="180">
          <template #default="{ row }">{{ row.usedTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="danger"
              plain
              @click="handleInvalidate(row)"
            >作废</el-button>
            <span v-else style="color: #c0c4cc; font-size: 13px;">—</span>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createInvite, getInviteList, invalidateInvite } from '@/api/order'
import { getUserId } from '@/api/user'

const loading = ref(false)
const generating = ref(false)
const tableData = ref([])
const newInvite = ref(null)

const genForm = ref({ plateNo: '', expireHours: 24 })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const statusMap = { 0: '有效', 1: '已使用', 2: '已过期', 3: '已作废' }
const statusTagMap = { 0: 'success', 1: 'info', 2: 'danger', 3: 'warning' }
onMounted(() => loadData())

const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    const res = await getInviteList({
      current: pagination.current,
      size: pagination.size,
      userId
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!genForm.value.plateNo.trim()) {
    ElMessage.warning('请输入访客车牌号')
    return
  }
  generating.value = true
  try {
    const res = await createInvite(getUserId(), genForm.value.plateNo.trim(), genForm.value.expireHours)
    newInvite.value = res.data
    genForm.value.plateNo = ''
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const copyCode = async (code) => {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('邀请码已复制')
  } catch {
    ElMessage.info(`邀请码：${code}`)
  }
}

const handleInvalidate = async (row) => {
  await ElMessageBox.confirm(`确定作废邀请码 ${row.inviteCode} 吗？`, '提示', { type: 'warning' })
  await invalidateInvite(row.id, getUserId())
  ElMessage.success('已作废')
  loadData()
}
</script>

<style lang="scss" scoped>
.gen-card { margin-bottom: 16px; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.invite-result {
  display: flex;
  align-items: center;
  gap: 8px;

  .invite-code {
    font-size: 20px;
    letter-spacing: 2px;
    color: #67c23a;
  }
}
</style>
