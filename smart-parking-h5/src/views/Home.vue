<template>
  <div class="page">
    <van-nav-bar title="智慧停车" fixed placeholder />

    <div class="page-content">
      <!-- 欢迎卡片 -->
      <div class="welcome-card">
        <div class="hello">你好，{{ userInfo.realName || '业主' }}</div>
        <div class="plate-tip">{{ ongoingCount }} 辆车停车中 · {{ unread }} 条未读通知</div>
      </div>

      <!-- 待缴提醒 -->
      <van-notice-bar
        v-if="pendingPayOrders.length"
        left-icon="gold-coin-o"
        color="#d4237a"
        background="#fff7f8"
        :scrollable="false"
      >
        您有 {{ pendingPayOrders.length }} 笔订单待缴费，共 ¥{{ pendingAmount }}
      </van-notice-bar>

      <!-- 功能入口 -->
      <van-grid :column-num="4" :border="false" class="entry-grid">
        <van-grid-item icon="van" text="我的车辆" @click="router.push('/vehicles')" />
        <van-grid-item icon="orders-o" text="我的订单" @click="router.push('/orders')" />
        <van-grid-item icon="friends-o" text="访客邀请" @click="router.push('/invite')" />
        <van-grid-item icon="bell" :badge="unread || ''" text="通知中心" @click="router.push('/notify')" />
      </van-grid>

      <!-- 进行中订单 -->
      <van-cell-group inset title="进行中订单">
        <template v-if="ongoingOrders.length">
          <van-cell
            v-for="o in ongoingOrders"
            :key="o.orderNo"
            :title="o.plateNo"
            :label="`车位 ${o.spaceNo || '-'} · 入场 ${o.entryTime}`"
            @click="router.push('/orders')"
          >
            <template #value>
              <van-tag type="primary">停车中</van-tag>
            </template>
          </van-cell>
        </template>
        <van-empty v-else description="暂无进行中的订单" :image-size="80" />
      </van-cell-group>
    </div>

    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/vehicles" icon="van">车辆</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o" :badge="pendingPayOrders.length || ''">订单</van-tabbar-item>
      <van-tabbar-item replace to="/invite" icon="friends-o">访客</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo, getUserId } from '@/api/user'
import { getVehicleList } from '@/api/vehicle'
import { getOrderList } from '@/api/order'
import { getUnreadCount } from '@/api/notify'

const router = useRouter()
const userId = getUserId()
const userInfo = ref({})

const vehicles = ref([])
const allOrders = ref([])
const unread = ref(0)

// 进行中的订单
const ongoingOrders = computed(() => allOrders.value.filter(o => o.status === 0))
// 待缴费（已完成但未支付）
const pendingPayOrders = computed(() => allOrders.value.filter(o => o.status === 1 && o.payStatus === 0))
const pendingAmount = computed(() => pendingPayOrders.value.reduce((s, o) => s + (Number(o.amount) || 0), 0).toFixed(2))
const ongoingCount = computed(() => ongoingOrders.value.length)

onMounted(async () => {
  try {
    const userRes = await getUserInfo(userId)
    if (userRes.code === 200) userInfo.value = userRes.data
  } catch { /* ignore */ }

  try {
    const vRes = await getVehicleList({ userId, current: 1, size: 50 })
    if (vRes.code === 200) {
      vehicles.value = vRes.data.records || []
      if (vehicles.value.length) {
        const ids = vehicles.value.map(v => v.id).join(',')
        const oRes = await getOrderList({ vehicleIds: ids, current: 1, size: 50 })
        if (oRes.code === 200) allOrders.value = oRes.data.records || []
      }
    }
  } catch { /* ignore */ }

  try {
    const nRes = await getUnreadCount(userId)
    if (nRes.code === 200) unread.value = nRes.data || 0
  } catch { /* ignore */ }
})
</script>

<style scoped>
.welcome-card {
  background: linear-gradient(135deg, #1989fa, #2a6ef2);
  border-radius: 12px;
  color: #fff;
  padding: 18px 16px;
  margin-bottom: 12px;
}

.hello {
  font-size: 20px;
  font-weight: 600;
}

.plate-tip {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 6px;
}

.entry-grid {
  margin: 12px 0;
  background: #fff;
  border-radius: 12px;
}
</style>
