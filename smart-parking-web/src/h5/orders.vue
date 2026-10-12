<template>
  <div>
    <van-nav-bar title="我的订单" />
    <van-tabs v-model:active="tab">
      <van-tab title="进行中" />
      <van-tab title="历史" />
    </van-tabs>

    <van-cell v-for="o in list" :key="o.id" :title="o.plateNo + ' · ' + o.spaceNo" :label="`${format(o.entryTime)} 入场`" style="margin-top: 8px;">
      <template #value>
        <van-tag :type="payTagType(o)">{{ payLabel(o) }}</van-tag>
      </template>
      <template #label>
        <div v-if="o.amount" style="margin-top: 4px;">¥{{ o.amount }}</div>
      </template>
      <template #right-icon>
        <van-button v-if="o.status === 1 && o.payStatus === 0" size="small" type="danger" @click="pay(o)">缴费</van-button>
      </template>
    </van-cell>
    <van-empty v-if="!list.length" description="暂无订单" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import request from '@/utils/request'

const tab = ref(0)
const list = ref([])
const tabs = [
  { status: 1, payStatus: null },
  { status: 2, payStatus: null }
]

function format(t) { return t ? new Date(t).toLocaleString('zh-CN') : '-' }
function payTagType(o) { return o.payStatus === 1 ? 'success' : 'warning' }
function payLabel(o) { return o.payStatus === 1 ? '已支付' : '待支付' }

async function load() {
  try {
    const t = tabs[tab.value]
    list.value = (await request.get('/order/list', {
      params: { status: t.status, size: 20 }
    })).data?.records || []
  } catch (e) { showToast('加载失败') }
}

async function pay(o) {
  try {
    await showConfirmDialog({ title: '确认缴费', message: `支付 ¥${o.amount}？` })
    const pay = (await request.post('/payment/create', null, {
      params: { orderId: o.id, amount: o.amount, payType: 1 }
    })).data
    await request.post('/payment/wechat/pay', null, { params: { payNo: pay.payNo } })
    showToast('支付成功')
    load()
  } catch (e) { /* canceled */ }
}

watch(tab, load)
onMounted(load)
</script>
