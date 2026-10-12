<template>
  <div class="parking-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="区域">
          <el-select v-model="searchForm.area" placeholder="全部区域" clearable>
            <el-option label="A区" value="A区" />
            <el-option label="B区" value="B区" />
            <el-option label="C区" value="C区" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.type" placeholder="全部类型" clearable>
            <el-option label="固定车位" :value="0" />
            <el-option label="临时车位" :value="1" />
            <el-option label="访客车位" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
            <el-option label="空闲" :value="0" />
            <el-option label="已占用" :value="1" />
            <el-option label="预约中" :value="2" />
            <el-option label="维护中" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleAdd">新增车位</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 视图切换 -->
    <el-card shadow="never" class="search-card">
      <el-radio-group v-model="viewMode">
        <el-radio-button value="list">列表视图</el-radio-button>
        <el-radio-button value="map">地图视图</el-radio-button>
      </el-radio-group>
      <el-text type="info" style="margin-left:16px">
        <el-tag size="small" color="#67c23a" style="color:#fff">空闲</el-tag>
        <el-tag size="small" color="#409eff" style="color:#fff;margin-left:8px">占用</el-tag>
        <el-tag size="small" color="#e6a23c" style="color:#fff;margin-left:8px">预约</el-tag>
        <el-tag size="small" color="#f56c6c" style="color:#fff;margin-left:8px">维护</el-tag>
      </el-text>
    </el-card>

    <!-- 地图视图 -->
    <el-card v-if="viewMode === 'map'" shadow="never" style="margin-bottom:16px">
      <div class="parking-map">
        <div v-for="space in tableData" :key="space.id"
             class="space-cell"
             :class="spaceClass(space.status)"
             :title="`${space.spaceNo} | ${statusMap[space.status]}`">
          {{ space.spaceNo }}
        </div>
      </div>
    </el-card>

    <!-- 车位列表 -->
    <el-card v-if="viewMode === 'list'" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="spaceNo" label="车位编号" width="120" />
        <el-table-column prop="area" label="区域" width="100" />
        <el-table-column prop="floor" label="楼层" width="80" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.type]">{{ typeMap[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="monthPrice" label="月租价格" width="100">
          <template #default="{ row }">
            {{ row.monthPrice ? `¥${row.monthPrice}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="车位编号" prop="spaceNo">
          <el-input v-model="formData.spaceNo" placeholder="请输入车位编号" />
        </el-form-item>
        <el-form-item label="区域" prop="area">
          <el-select v-model="formData.area" placeholder="请选择区域">
            <el-option label="A区" value="A区" />
            <el-option label="B区" value="B区" />
            <el-option label="C区" value="C区" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层" prop="floor">
          <el-input v-model="formData.floor" placeholder="如：B1、1F" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择类型">
            <el-option label="固定车位" :value="0" />
            <el-option label="临时车位" :value="1" />
            <el-option label="访客车位" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="月租价格">
          <el-input-number v-model="formData.monthPrice" :min="0" :precision="2" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSpaceList, addSpace, updateSpace, deleteSpace } from '@/api/parking'

const loading = ref(false)
const tableData = ref([])
const viewMode = ref('list')
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const isEdit = ref(false)

const typeMap = { 0: '固定车位', 1: '临时车位', 2: '访客车位' }
const statusMap = { 0: '空闲', 1: '已占用', 2: '预约中', 3: '维护中' }
const typeTagMap = { 0: '', 1: 'success', 2: 'warning' }
const statusTagMap = { 0: 'success', 1: '', 2: 'warning', 3: 'info' }

const spaceClass = (status) => {
  return ['st0', 'st1', 'st2', 'st3'][status] || 'st0'
}

const searchForm = reactive({ area: '', type: '', status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const formData = ref({ spaceNo: '', area: '', floor: '', type: 1, monthPrice: null })
const formRules = {
  spaceNo: [{ required: true, message: '请输入车位编号', trigger: 'blur' }],
  area: [{ required: true, message: '请选择区域', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const res = await getSpaceList({
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
const resetSearch = () => { Object.assign(searchForm, { area: '', type: '', status: '' }); handleSearch() }

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增车位'
  formData.value = { spaceNo: '', area: '', floor: '', type: 1, monthPrice: null }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑车位'
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const form = formRef.value
  if (!form) return
  await form.validate(async (valid) => {
    if (!valid) return
    if (isEdit.value) {
      await updateSpace(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await addSpace(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定要删除车位 ${row.spaceNo} 吗？`, '提示', { type: 'warning' })
  await deleteSpace(row.id)
  ElMessage.success('删除成功')
  loadData()
}
</script>

<style lang="scss" scoped>
.search-card {
  margin-bottom: 16px;
}
.parking-map {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
  padding: 10px;
}
.space-cell {
  height: 60px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  transition: transform .15s;
  &:hover { transform: scale(1.05); }
}
.st0 { background: #67c23a; }
.st1 { background: #409eff; }
.st2 { background: #e6a23c; }
.st3 { background: #f56c6c; }
</style>
