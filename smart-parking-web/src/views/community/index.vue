<template>
  <div class="community-page">
    <el-card shadow="never" class="search-card">
      <el-button type="success" @click="handleAdd">新增小区</el-button>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="小区名称" min-width="160" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column prop="spacesTotal" label="车位总数" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '运营中' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const loading = ref(false)
const tableData = ref([])

onMounted(loadData)

const loadData = async () => {
  loading.value = true
  try {
    const res = await request({ url: '/user/community/list', method: 'get' })
    tableData.value = res.data || []
  } finally { loading.value = false }
}

const handleAdd = () => {
  // 简化：直接弹窗
}
</script>
