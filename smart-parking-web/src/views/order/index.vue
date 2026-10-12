<template>
  <div class="order-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="订单状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="进行中" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="已取消" :value="2" />
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
        <el-table-column prop="orderNo" label="订单编号" width="200" />
        <el-table-column prop="plateNo" label="车牌号" width="120" />
        <el-table-column prop="spaceNo" label="车位号" width="100" />
        <el-table-column prop="entryTime" label="入场时间" width="180" />
        <el-table-column prop="exitTime" label="出场时间" width="180">
          <template #default="{ row }">
            {{ row.exitTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分)" width="100">
          <template #default="{ row }">
            {{ row.duration || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">
            {{ row.amount ? `¥${row.amount}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="payStatus" label="支付状态" width="100">
          <template #default="{ row }">
            <el-tag :type="payStatusTagMap[row.payStatus]">{{ payStatusMap[row.payStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status]">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" type="success" @click="handleExit(row)">模拟出场</el-button>
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

    <el-dialog v-model="detailVisible" title="订单详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单编号">{{ detailData.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">{{ detailData.plateNo }}</el-descriptions-item>
        <el-descriptions-item label="车位号">{{ detailData.spaceNo }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="statusTagMap[detailData.status]">{{ statusMap[detailData.status] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="入场时间">{{ detailData.entryTime }}</el-descriptions-item>
        <el-descriptions-item label="出场时间">{{ detailData.exitTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停车时长">{{ detailData.duration ? `${detailData.duration}分钟` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">{{ detailData.amount ? `¥${detailData.amount}` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">
          <el-tag :type="payStatusTagMap[detailData.payStatus]">{{ payStatusMap[detailData.payStatus] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 去支付弹窗 -->
    <el-dialog v-model="payVisible" title="订单支付" width="440px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="订单编号">{{ payOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">{{ payOrder.plateNo }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <span style="color: #f56c6c; font-weight: 600; font-size: 16px;">¥{{ payOrder.amount }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-radio-group v-model="payType" class="pay-type-group">
        <el-radio :value="1" border>微信支付</el-radio>
        <el-radio :value="2" border>支付宝</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="handleConfirmPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, getOrderDetail, exitOrder } from '@/api/order'
import { createPayment, wechatPay, alipayPay } from '@/api/payment'

const loading = ref(false)
const tableData = ref([])
const detailVisible = ref(false)
const detailData = ref({})

const statusMap = { 0: '进行中', 1: '已完成', 2: '已取消' }
const payStatusMap = { 0: '未支付', 1: '已支付', 2: '已退款' }
const statusTagMap = { 0: 'warning', 1: 'success', 2: 'info' }
const payStatusTagMap = { 0: 'warning', 1: 'success', 2: 'danger' }

const searchForm = reactive({ status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

// 支付
const payVisible = ref(false)
const paying = ref(false)
const payType = ref(1)
const payOrder = ref({})

const getUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    return JSON.parse(atob(token.split('.')[1])).userId
  } catch { return null }
}

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    const res = await getOrderList({
      current: pagination.current,
      size: pagination.size,
      status: searchForm.status
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.status = ''; handleSearch() }

const handleDetail = async (row) => {
  const res = await getOrderDetail(row.orderNo)
  detailData.value = res.data
  detailVisible.value = true
}

// 模拟出场：结算停车费并完成订单
const handleExit = async (row) => {
  await ElMessageBox.confirm(`确定车辆 ${row.plateNo} 出场并结算费用吗？`, '模拟出场', { type: 'warning' })
  const res = await exitOrder(row.orderNo)
  ElMessage.success(`出场成功，应付 ¥${res.data.amount}`)
  loadData()
}

// 去支付：已完成未支付订单
const handlePay = (row) => {
  payOrder.value = row
  payType.value = 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  if (payOrder.value.amount == null) {
    ElMessage.warning('订单金额未结算，请先出场')
    return
  }
  paying.value = true
  try {
    // 创建支付记录（待支付）
    const createRes = await createPayment({
      orderId: payOrder.value.id,
      userId: getUserId(),
      amount: payOrder.value.amount,
      payType: payType.value
    })
    const payNo = createRes.data.payNo
    // 模拟扫码支付
    const payRes = payType.value === 1
      ? await wechatPay(payNo)
      : await alipayPay(payNo)
    ElMessage.success(`支付成功：${payRes.data.message}，流水 ${payNo}`)
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
  gap: 12px;
}
</style>
