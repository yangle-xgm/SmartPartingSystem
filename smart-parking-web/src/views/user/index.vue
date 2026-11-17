<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Users"
      title="用户管理"
      desc="业主、物业与超管统一管理。手机号在库内 AES 加密、接口脱敏返回；编辑时手机号留空表示不修改，避免把掩码写回数据库。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="用户总数" :value="stats.total" unit="人" icon="User" tone="brand" hint="含全部角色" />
      <StatCard label="业主" :value="stats.owner" unit="人" icon="HomeFilled" tone="teal" hint="角色 = 0" />
      <StatCard label="物业 / 超管" :value="stats.admin" unit="人" icon="Avatar" tone="gold" hint="角色 = 1 / 2" />
      <StatCard label="禁用账号" :value="stats.disabled" unit="人" icon="CircleClose" tone="danger" hint="无法登录" />
    </div>

    <div class="sp-toolbar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="模糊搜索" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="全部" clearable style="width: 130px">
            <el-option label="业主" :value="0" />
            <el-option label="物业管理员" :value="1" />
            <el-option label="超级管理员" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="sp-toolbar__right">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <section class="sp-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="用户" min-width="170">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="30" :src="row.avatar" class="user-cell__avatar">
                {{ (row.realName || row.username || 'U').charAt(0) }}
              </el-avatar>
              <div>
                <b>{{ row.realName || row.username }}</b>
                <span>@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagMap[row.role]" effect="light">{{ roleMap[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">
            <span class="sp-num">{{ row.phoneMasked || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 1 ? 'free' : 'fix'" />
              {{ row.status === 1 ? '正常' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">没有匹配的用户</span>
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

    <el-dialog v-model="editVisible" title="编辑用户" width="460px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="用户名">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="editForm.realName" maxlength="20" placeholder="留空则保持原姓名" />
          <p v-if="editForm.nameMasked" class="form-hint">当前：{{ editForm.currentName }}（脱敏展示，留空不会覆盖原姓名）</p>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="留空表示不修改" maxlength="11" />
          <p class="form-hint">当前：{{ editForm.phoneMasked || '未绑定' }}</p>
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="roleMap[editForm.role]" disabled />
          <p class="form-hint">角色变更需由数据库或运维流程调整，避免越权</p>
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
import { onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getUserList, updateUser } from '@/api/user'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const editVisible = ref(false)
const editFormRef = ref(null)

const roleMap = { 0: '业主', 1: '物业管理员', 2: '超级管理员' }
const roleTagMap = { 0: 'primary', 1: 'warning', 2: 'danger' }

const searchForm = reactive({ username: '', role: '', status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })
const stats = reactive({ total: 0, owner: 0, admin: 0, disabled: 0 })

const editForm = ref({ id: null, username: '', realName: '', nameMasked: false, currentName: '', phone: '', phoneMasked: '', role: 0 })
const editRules = {
  realName: [{
    validator: (rule, value, cb) => {
      // 原姓名是脱敏值时允许留空（表示保持原值）
      if (!value && !editForm.value.nameMasked) cb(new Error('请输入真实姓名'))
      else cb()
    },
    trigger: 'blur'
  }],
  // 留空不校验；填写时必须是 11 位手机号
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

onMounted(() => {
  loadData()
  loadStats()
})

const buildParams = () => {
  const params = { current: pagination.current, size: pagination.size }
  if (searchForm.username) params.username = searchForm.username
  if (searchForm.role !== '') params.role = searchForm.role
  if (searchForm.status !== '') params.status = searchForm.status
  return params
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUserList(buildParams())
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// 概览：单独取一批（上限 200）用于角色/状态分布，避免影响分页查询
const loadStats = async () => {
  try {
    const res = await getUserList({ current: 1, size: 200 })
    const list = res.data?.records || []
    stats.total = res.data?.total || list.length
    stats.owner = list.filter(u => u.role === 0).length
    stats.admin = list.filter(u => u.role === 1 || u.role === 2).length
    stats.disabled = list.filter(u => u.status === 0).length
  } catch (e) { /* 忽略 */ }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => {
  Object.assign(searchForm, { username: '', role: '', status: '' })
  handleSearch()
}

// 姓名若已被后端脱敏（如「张*」），不要回填输入框：管理员一保存就会把真名覆盖成脱敏串
const isMaskedName = (v) => typeof v === 'string' && v.includes('*')

const handleEdit = (row) => {
  const masked = isMaskedName(row.realName)
  editForm.value = {
    id: row.id,
    username: row.username,
    realName: masked ? '' : (row.realName || ''),
    nameMasked: masked,
    currentName: row.realName || '',
    phone: '',
    phoneMasked: row.phoneMasked || '',
    role: row.role
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
      // PUT /api/user/update，仅提交需要变更的字段；手机号为空则不下发，防止写入脱敏串
      await updateUser({
        id: editForm.value.id,
        ...(editForm.value.realName && editForm.value.realName.trim()
          ? { realName: editForm.value.realName.trim() }
          : {}),
        ...(editForm.value.phone ? { phone: editForm.value.phone } : {})
      })
      ElMessage.success('保存成功')
      editVisible.value = false
      loadData()
      loadStats()
    } catch (e) {
      toastOnce(e, '保存失败')
    } finally {
      saving.value = false
    }
  })
}
</script>

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell__avatar {
  background: var(--sp-grad-brand);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex: none;
}

.user-cell b {
  display: block;
  font-size: 13px;
  color: var(--sp-text);
}

.user-cell span {
  font-size: 11px;
  color: var(--sp-text-3);
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
  margin-top: 4px;
}
</style>
