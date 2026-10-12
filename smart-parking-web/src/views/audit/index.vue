<template>
  <div class="audit-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="操作人">
          <el-input v-model="searchForm.username" placeholder="用户名" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.operation" placeholder="全部" clearable style="width: 160px">
            <el-option label="登录" value="登录" />
            <el-option label="注册" value="注册" />
            <el-option label="退出登录" value="退出登录" />
            <el-option label="修改用户" value="修改用户" />
            <el-option label="绑定车辆" value="绑定车辆" />
            <el-option label="解绑车辆" value="解绑车辆" />
            <el-option label="车牌识别" value="车牌识别" />
            <el-option label="车辆入场" value="车辆入场" />
            <el-option label="车辆出场" value="车辆出场" />
            <el-option label="取消订单" value="取消订单" />
            <el-option label="发起支付" value="发起支付" />
            <el-option label="支付成功" value="支付成功" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="searchForm.isSuccess" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="createTime" label="操作时间" width="180" />
        <el-table-column prop="username" label="操作人" width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operation" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="operationTag(row.operation)">{{ row.operation }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="method" label="请求方法" width="200" show-overflow-tooltip />
        <el-table-column prop="params" label="参数" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.params || '-' }}</template>
        </el-table-column>
        <el-table-column prop="isSuccess" label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isSuccess === 1 ? 'success' : 'danger'">{{ row.isSuccess === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时(ms)" width="90">
          <template #default="{ row }">{{ row.costTime ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
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
import { getAuditList } from '@/api/audit'

const loading = ref(false)
const tableData = ref([])

const searchForm = reactive({ username: '', operation: '', isSuccess: '' })
const timeRange = ref(null)
const pagination = reactive({ current: 1, size: 10, total: 0 })

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      username: searchForm.username || undefined,
      operation: searchForm.operation || undefined,
      isSuccess: searchForm.isSuccess === '' ? undefined : searchForm.isSuccess
    }
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res = await getAuditList(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }

const resetSearch = () => {
  searchForm.username = ''
  searchForm.operation = ''
  searchForm.isSuccess = ''
  timeRange.value = null
  handleSearch()
}

// 不同类型操作标签色
const operationTag = (op) => {
  if (['登录', '退出登录', '注册'].includes(op)) return ''
  if (['车辆入场', '车辆出场'].includes(op)) return 'success'
  if (['车牌识别', '绑定车辆', '解绑车辆'].includes(op)) return 'warning'
  if (['发起支付', '支付成功'].includes(op)) return 'danger'
  return 'info'
}
</script>

<style lang="scss" scoped>
.search-card { margin-bottom: 16px; }
</style>
