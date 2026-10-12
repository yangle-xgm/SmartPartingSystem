<template>
  <div class="user-page">
    <el-card shadow="never">
      <template #header>
        <span>用户列表</span>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="120" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="phoneMasked" label="手机号" width="130" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagMap[row.role]">{{ roleMap[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
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

    <el-dialog v-model="editVisible" title="编辑用户" width="420px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="用户名">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="editForm.realName" placeholder="请输入真实姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserList, updateUser } from '@/api/user'

const loading = ref(false)
const tableData = ref([])

const roleMap = { 0: '业主', 1: '管理员', 2: '超级管理员' }
const roleTagMap = { 0: '', 1: 'warning', 2: 'danger' }

const pagination = reactive({ current: 1, size: 10, total: 0 })

// 编辑
const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editForm = ref({ id: null, username: '', realName: '', phone: '' })
const editRules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      current: pagination.current,
      size: pagination.size
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleEdit = (row) => {
  editForm.value = {
    id: row.id,
    username: row.username,
    realName: row.realName || '',
    phone: row.phoneMasked || ''
  }
  editVisible.value = true
}

const handleSave = async () => {
  const form = editFormRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await updateUser({
        id: editForm.value.id,
        realName: editForm.value.realName,
        phone: editForm.value.phone
      })
      ElMessage.success('保存成功')
      editVisible.value = false
      loadData()
    } catch (e) {
      ElMessage.error(e.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}
</script>
