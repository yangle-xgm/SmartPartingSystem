<template>
  <div>
    <van-nav-bar title="车牌人工确认" />
    <van-notice-bar style="margin-top: 8px;" text="道闸识别置信度不足，请选择正确车牌后入场" />

    <van-cell-group inset style="margin-top: 12px;" title="疑似车牌">
      <van-cell v-for="(p, i) in suggestList" :key="i" :title="p" is-link @click="confirm(p)">
        <template #value>
          <van-button size="small" type="primary">确认</van-button>
        </template>
      </van-cell>
      <van-empty v-if="!suggestList.length" description="无候选车牌，请手动输入" />
    </van-cell-group>

    <van-cell-group inset title="手动输入" style="margin-top: 12px;">
      <van-field v-model="manual" placeholder="请输入完整车牌号" />
      <van-button block type="primary" style="margin: 12px;" @click="confirm(manual)">
        确认入场
      </van-button>
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const router = useRouter()
const suggestList = ref(JSON.parse(sessionStorage.getItem('h5Suggest') || '[]'))
const manual = ref('')

async function confirm(plate) {
  if (!plate) { showToast('请输入车牌'); return }
  try {
    await request.post('/order/internal/confirm-plate', null, { params: { plateNo: plate } })
    showToast('已确认入场')
    sessionStorage.removeItem('h5Suggest')
    router.push('/h5/home')
  } catch (e) {}
}
</script>
