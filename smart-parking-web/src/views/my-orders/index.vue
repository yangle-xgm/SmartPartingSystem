<template>
  <div class="my-order-page">
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
        <el-table-column prop="orderNo" label="订单编号" width="190" />
        <el-table-column prop="plateNo" label="车牌号" width="120" />
        <el-table-column prop="spaceNo" label="车位号" width="90" />
        <el-table-column prop="entryTime" label="入场时间" width="180" />
        <el-table-column prop="exitTime" label="出场时间" width="180">
          <template #default="{ row }">{{ row.exitTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分)" width="90">
          <template #default="{ row }">{{ row.duration || '-' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100">
          <template #default="{ row }">{{ row.amount ? `¥${row.amount}` : '-' }}</template>
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="success"
              @click="handleExit(row)"
            >模拟出场</el-button>
            <el-button
              v-if="row.status === 1 && row.payStatus === 0"
              size="small"
              type="warning"
              @click="handlePay(row)"
            >去支付</el-button>
            <span v-if="row.status === 1 && row.payStatus === 1" style="color:#67c23a;font-size:13px;">已完成</span>
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

    <!-- 在线缴费弹窗 -->
    <el-dialog v-model="payVisible" title="在线缴费" width="440px">
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
import { getOrderList, exitOrder } from '@/api/order'
import { getVehicleList } from '@/api/vehicle'
import { createPayment, wechatPay, alipayPay } from '@/api/payment'
import { getUserId } from '@/api/user'

const loading = ref(false)
const tableData = ref([])

const statusMap = { 0: '进行中', 1: '已完成', 2: '已取消' }
const payStatusMap = { 0: '未支付', 1: '已支付', 2: '已退款' }
const statusTagMap = { 0: 'warning', 1: 'success', 2: 'info' }
const payStatusTagMap = { 0: 'warning', 1: 'success', 2: 'danger' }

const searchForm = reactive({ status: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

// 在线缴费
const payVisible = ref(false)
const paying = ref(false)
const payType = ref(1)
const payOrder = ref({})

onMounted(() => loadData())

// 我的订单 = 我的车辆产生的订单（vehicleIds 过滤）
const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    const vehicleRes = await getVehicleList(userId)
    const vehicleIds = (vehicleRes.data || []).map(v => v.id)
    const params = {
      current: pagination.current,
      size: pagination.size,
      status: searchForm.status
    }
    if (vehicleIds.length) params.vehicleIds = vehicleIds.join(',')
    const res = await getOrderList(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadData() }
const resetSearch = () => { searchForm.status = ''; handleSearch() }

// 模拟出场：结算停车费并完成订单
const handleExit = async (row) => {
  await ElMessageBox.confirm(`确定车辆 ${row.plateNo} 出场并结算费用吗？`, '模拟出场', { type: 'warning' })
  const res = await exitOrder(row.orderNo)
  ElMessage.success(`出场成功，应付 ¥${res.data.amount}`)
  loadData()
}

// 在线缴费（模拟扫码）
const handlePay = (row) => {
  payOrder.value = row
  payType.value = 1
  payVisible.value = true
}

const handleConfirmPay = async () => {
  paying.value = true
  try {
    const createRes = await createPayment({
      orderId: payOrder.value.id,
      userId: getUserId(),
      amount: payOrder.value.amount,
      payType: payType.value
    })
    const payNo = createRes.data.payNo
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
