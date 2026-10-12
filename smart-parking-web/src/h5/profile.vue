<template>
  <div>
    <van-nav-bar title="我的" />
    <van-cell-group inset style="margin-top: 12px;">
      <van-cell title="用户名" :value="user.username" />
      <van-cell title="姓名" :value="user.realName || '-'" />
      <van-cell title="手机号" :value="user.phone || '-'" />
      <van-cell title="角色" :value="['业主','物业','超管'][user.role] || '业主'" />
    </van-cell-group>

    <van-cell-group inset title="车辆管理" style="margin-top: 12px;">
      <van-cell v-for="v in vehicles" :key="v.id" :title="v.plateNo" :label="v.brand" />
      <van-button plain block style="margin: 12px;" @click="addVehicle">添加车辆</van-button>
    </van-cell-group>

    <van-cell-group inset title="访客邀请" style="margin-top: 12px;">
      <van-cell v-for="i in invites" :key="i.id" :title="i.inviteCode" :label="i.plateNo">
        <template #value>{{ i.expireHours }}h</template>
      </van-cell>
      <van-button plain block style="margin: 12px;" @click="addInvite">生成邀请码</van-button>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px;">
      <van-cell title="退出登录" is-link @click="logout" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showToast, showDialog } from 'vant'
import request from '@/utils/request'
import router from '@/router'

const user = ref({})
const vehicles = ref([])
const invites = ref([])

async function load() {
  try {
    user.value = (await request.get('/user/info')).data || {}
    vehicles.value = (await request.get('/vehicle/my')).data || []
    invites.value = (await request.get('/order/invite/list')).data?.records || []
  } catch (e) {}
}

async function addVehicle() {
  showToast('请在管理端 Web 添加车辆')
}
async function addInvite() {
  showToast('请在管理端 Web 生成邀请码')
}

function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}

onMounted(load)
</script>
