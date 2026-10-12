<template>
  <div>
    <van-nav-bar title="车位预约" />
    <van-tabs v-model:active="area">
      <van-tab v-for="a in areas" :key="a" :title="a" />
    </van-tabs>

    <van-cell-group v-for="s in spaces" :key="s.id" inset style="margin-top: 8px;">
      <van-cell :title="s.spaceNo" :label="s.area + '区'">
        <template #value>
          <van-tag :type="s.status === 0 ? 'success' : s.status === 2 ? 'warning' : 'danger'">
            {{ ['空闲','占用','预约','维护'][s.status] }}
          </van-tag>
        </template>
        <template #right-icon>
          <van-button v-if="s.status === 0" size="small" type="primary" @click="doReserve(s)">预约</van-button>
        </template>
      </van-cell>
    </van-cell-group>
    <van-empty v-if="!spaces.length" description="该区域暂无可预约车位" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import request from '@/utils/request'

const areas = ['A区', 'B区']
const area = ref(0)
const spaces = ref([])

async function load() {
  try {
    spaces.value = (await request.get('/parking/available', { params: { area: areas[area.value] } })).data || []
  } catch (e) { showToast('加载失败') }
}

async function doReserve(s) {
  try {
    await showConfirmDialog({ title: '确认预约', message: `预约 ${s.spaceNo}？预约后24小时内有效` })
    await request.post('/parking/reserve', null, { params: { spaceId: s.id } })
    showToast('预约成功')
    load()
  } catch (e) { /* canceled */ }
}

watch(area, load)
onMounted(load)
</script>
