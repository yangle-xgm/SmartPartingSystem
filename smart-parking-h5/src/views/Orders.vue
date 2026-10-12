<template>
  <div class="page">
    <van-nav-bar title="我的订单" fixed placeholder />

    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="全部" />
      <van-tab title="进行中" />
      <van-tab title="待缴费" />
      <van-tab title="已完成" />
    </van-tabs>

    <div class="page-content">
      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="filteredOrders.length">
          <div v-for="o in filteredOrders" :key="o.orderNo" class="order-card">
            <div class="order-head">
              <span class="plate">{{ o.plateNo }}</span>
              <van-tag v-if="o.status === 0" type="primary">停车中</van-tag>
              <van-tag v-else-if="o.status === 1 && o.payStatus === 0" type="danger">待缴费</van-tag>
              <van-tag v-else-if="o.status === 1 && o.payStatus === 1" type="success">已完成</van-tag>
              <van-tag v-else type="default">已取消</van-tag>
            </div>
            <div class="order-info">
              车位 {{ o.spaceNo || '-' }} ｜ 入场 {{ o.entryTime || '-' }}
            </div>
            <div v-if="o.exitTime" class="order-info">
              出场 {{ o.exitTime }} ｜ 时长 {{ o.duration ?? '-' }} 分钟
            </div>
            <div class="order-foot">
              <span class="amount">¥{{ o.amount != null ? Number(o.amount).toFixed(2) : '-' }}</span>
              <div>
                <van-button
                  v-if="o.status === 0"
                  size="small"
                  plain
                  type="danger"
                  @click="handleCancel(o)"
                >取消</van-button>
                <van-button
                  v-if="o.status === 1 && o.payStatus === 0"
                  size="small"
                  type="danger"
                  style="margin-left: 8px;"
                  @click="handlePay(o)"
                >去支付</van-button>
                <span v-if="o.status === 1 && o.payStatus === 1" class="paid-text">已支付</span>
              </div>
            </div>
            <div class="order-no">订单号 {{ o.orderNo }}</div>
          </div>
        </template>
        <van-empty v-else description="暂无订单" />
      </van-pull-refresh>
    </div>

    <!-- 支付方式弹窗 -->
    <van-action-sheet
      v-model:show="payVisible"
      :title="`支付 ¥${payAmount}`"
      :actions="payActions"
      cancel-text="取消"
      @select="onPaySelect"
    />

    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/vehicles" icon="van">车辆</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o" :badge="pendingCount || ''">订单</van-tabbar-item>
      <van-tabbar-item replace to="/invite" icon="friends-o">访客</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import { getOrderList, cancelOrder, createPayment, wechatPay, alipayPay } from '@/api/order'
import { getVehicleList } from '@/api/vehicle'
import { getUserId } from '@/api/user'

const userId = getUserId()
const activeTab = ref(0)
const refreshing = ref(false)
const allOrders = ref([])

// 支付弹窗
const payVisible = ref(false)
const payAmount = ref('0.00')
const payingOrder = ref(null)
const payActions = [
  { name: '微信支付（模拟）', subname: '扫码即支付成功', value: 1 },
  { name: '支付宝（模拟）', subname: '扫码即支付成功', value: 2 }
]

const filteredOrders = computed(() => {
  const tab = activeTab.value
  if (tab === 1) return allOrders.value.filter(o => o.status === 0)
  if (tab === 2) return allOrders.value.filter(o => o.status === 1 && o.payStatus === 0)
  if (tab === 3) return allOrders.value.filter(o => o.status === 1)
  return allOrders.value
})

const pendingCount = computed(() => allOrders.value.filter(o => o.status === 1 && o.payStatus === 0).length)

onMounted(loadData)

async function loadData() {
  refreshing.value = true
  try {
    const vRes = await getVehicleList({ userId, current: 1, size: 50 })
    const vehicles = vRes.code === 200 ? (vRes.data.records || []) : []
    let list = []
    if (vehicles.length) {
      const ids = vehicles.map(v => v.id).join(',')
      const oRes = await getOrderList({ vehicleIds: ids, current: 1, size: 100 })
      if (oRes.code === 200) list = oRes.data.records || []
    }
    allOrders.value = list
  } catch (e) {
    allOrders.value = []
  } finally {
    refreshing.value = false
  }
}

const handleCancel = async (o) => {
  try {
    const res = await cancelOrder(o.orderNo)
    if (res.code === 200) {
      showSuccessToast('订单已取消')
      loadData()
    }
  } catch (e) {
    showToast(e.message || '取消失败')
  }
}

const handlePay = (o) => {
  payingOrder.value = o
  payAmount.value = Number(o.amount || 0).toFixed(2)
  payVisible.value = true
}

const onPaySelect = async (action) => {
  payVisible.value = false
  const order = payingOrder.value
  try {
    // 1. 创建支付记录
    const createRes = await createPayment(order.id, userId, order.amount, action.value)
    if (createRes.code !== 200) return
    const payNo = createRes.data.payNo
    // 2. 模拟支付
    const payRes = action.value === 1 ? await wechatPay(payNo) : await alipayPay(payNo)
    if (payRes.code === 200) {
      showSuccessToast('支付成功')
      loadData()
    }
  } catch (e) {
    showToast(e.message || '支付失败')
  }
}
</script>

<style scoped>
.order-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plate {
  font-size: 16px;
  font-weight: 600;
}

.order-info {
  font-size: 13px;
  color: #969799;
  margin-top: 6px;
  line-height: 1.7;
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.amount {
  color: #ee0a24;
  font-size: 16px;
  font-weight: 600;
}

.paid-text {
  font-size: 12px;
  color: #c8c9cc;
}

.order-no {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 6px;
}
</style>
