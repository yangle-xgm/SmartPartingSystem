<template>
  <div>
    <van-nav-bar title="智慧停车" />
    <van-notice-bar v-if="current" wrapable :text="`车辆 ${current.plateNo} 在场中，车位 ${current.spaceNo}，已停 ${elapsed}`" />

    <van-cell-group inset title="我的车辆">
      <van-cell v-for="v in vehicles" :key="v.id" :title="v.plateNo" :label="v.brand + ' ' + v.model">
        <template #value>
          <van-tag v-if="v.isDefault" type="primary">默认</van-tag>
        </template>
      </van-cell>
      <van-cell v-if="!vehicles.length" title="未添加车辆" value="去我的页面添加" />
    </van-cell-group>

    <van-grid :column-num="2" :border="false" style="margin-top: 12px;">
      <van-grid-item icon="newspaper-o" text="车位预约" to="/h5/reserve" />
      <van-grid-item icon="orders-o" text="我的订单" to="/h5/orders" />
      <van-grid-item icon="cash-back-record" text="缴费" to="/h5/orders?tab=pay" />
      <van-grid-item icon="after-sale" text="访客邀请" to="/h5/profile" />
    </van-grid>

    <van-cell-group inset title="在场状态" style="margin-top: 12px;">
      <van-cell title="当前订单" :value="current ? current.orderNo : '无'" />
      <van-cell title="入场时间" :value="current ? formatTime(current.entryTime) : '-'" />
      <van-cell title="预计费用" :value="current ? '¥' + current.amount : '-'" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { showToast } from 'vant'
import request from '@/utils/request'

const vehicles = ref([])
const current = ref(null)
let timer = null

const elapsed = computed(() => {
  if (!current.value?.entryTime) return ''
  const s = Math.floor((Date.now() - new Date(current.value.entryTime).getTime()) / 1000)
  return `${Math.floor(s / 3600)}小时${Math.floor((s % 3600) / 60)}分`
})

function formatTime(t) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }

async function load() {
  try {
    vehicles.value = (await request.get('/vehicle/my')).data || []
    const list = (await request.get('/order/list', { params: { status: 1, size: 5 } })).data?.records || []
    current.value = list[0] || null
  } catch (e) { showToast('加载失败') }
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
})
import { onUnmounted } from 'vue'
onUnmounted(() => clearInterval(timer))
</script>
