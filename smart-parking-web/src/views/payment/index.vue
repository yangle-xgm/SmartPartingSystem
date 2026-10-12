<template>
  <div class="payment-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="支付状态">
          <el-select v-model="searchForm.payStatus" placeholder="全部" clearable>
            <el-option label="待支付" :value="0" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="2" />
            <el-option label="已退款" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="payNo" label="支付流水号" width="200" />
        <el-table-column prop="orderId" label="订单ID" width="120" />
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="payType" label="支付方式" width="100">
          <template #default="{ row }">
            <el-tag>{{ payTypeMap[row.payType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="payStatusTagMap[row.payStatus]">{{ payStatusMap[row.payStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="transactionId" label="第三方交易号" width="180" />
        <el-table-column prop="payTime" label="支付时间" width="180" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="getRole() === 0 && row.payStatus === 0" size="small" type="warning" @click="handlePay(row)">去支付</el-button>
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
    <el-dialog v-model="payVisible" title="模拟支付" width="400px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="支付流水号">{{ payRecord.payNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ payRecord.orderId }}</el-descriptions-item>
        <el-descriptions-item label="支付金额">
          <span style="color: #f56c6c; font-weight: 600; font-size: 16px;">¥{{ payRecord.amount }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="pay-type-group">
        <el-radio-group v-model="payType">
          <el-radio :value="1" border>微信支付</el-radio>
          <el-radio :value="2" border>支付宝</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="handleConfirmPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaymentList, wechatPay, alipayPay } from '@/api/payment'
import { getUserId, getRole } from '@/api/user'

const loading = ref(false)
const tableData = ref([])

const payTypeMap = { 1: '微信', 2: '支付宝', 3: '现金' }
const payStatusMap = { 0: '待支付', 1: '成功', 2: '失败', 3: '已退款' }
const payStatusTagMap = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }

const searchForm = reactive({ payStatus: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

// 支付
const payVisible = ref(false)
const paying = ref(false)
const payType = ref(1)
const payRecord = ref({})

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      payStatus: searchForm.payStatus
    }
    // 业主只看自己的缴费记录；管理员/超管查看全部
    if (getRole() === 0) params.userId = getUserId()
    const res = await getPaymentList(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.payStatus = ''; handleSearch() }

// 待支付记录 → 模拟扫码支付
const handlePay = (row) => {
  payRecord.value = row
  payType.value = row.payType === 2 ? 2 : 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  paying.value = true
  try {
    const res = payType.value === 1
      ? await wechatPay(payRecord.value.payNo)
      : await alipayPay(payRecord.value.payNo)
    ElMessage.success(`支付成功：${res.data.message}`)
    payVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style lang="scss" scoped>
.search-card { margin-bottom: 16px; }

.pay-type-group {
  margin-top: 16px;
  display: flex;
}
</style>
