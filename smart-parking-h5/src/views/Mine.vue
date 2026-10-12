<template>
  <div class="page">
    <van-nav-bar title="我的" fixed placeholder />

    <div class="mine-header">
      <div class="avatar">{{ (userInfo.realName || userInfo.username || '业')[0] }}</div>
      <div class="name">{{ userInfo.realName || '业主' }}</div>
      <div class="account">{{ userInfo.username || '' }}</div>
    </div>

    <van-cell-group inset style="margin-top: 16px;">
      <van-cell title="手机号" :value="userInfo.phone || '-'" icon="phone-o" />
      <van-cell title="角色" :value="roleMap[userInfo.role] || '-'" icon="manager-o" />
      <van-cell title="注册时间" :value="userInfo.createTime || '-'" icon="clock-o" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 16px;">
      <van-cell title="通知中心" is-link icon="bell" to="/notify" />
    </van-cell-group>

    <div class="btn-area">
      <van-button block round plain type="danger" @click="handleLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getUserInfo, getUserId, logout } from '@/api/user'

const router = useRouter()
const userId = getUserId()
const userInfo = ref({})
const roleMap = { 0: '业主', 1: '管理员', 2: '超级管理员' }

onMounted(async () => {
  try {
    const res = await getUserInfo(userId)
    if (res.code === 200) userInfo.value = res.data
  } catch { /* ignore */ }
})

const handleLogout = async () => {
  await showConfirmDialog({
    title: '退出登录',
    message: '确定退出当前账号吗？'
  })
  try {
    await logout(userId)
  } catch { /* ignore */ }
  localStorage.removeItem('token')
  showToast('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.mine-header {
  background: linear-gradient(135deg, #1989fa, #2a6ef2);
  padding: 32px 0 28px;
  text-align: center;
  color: #fff;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  font-size: 28px;
  line-height: 64px;
  margin: 0 auto 10px;
}

.name {
  font-size: 18px;
  font-weight: 600;
}

.account {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 4px;
}

.btn-area {
  padding: 24px;
}
</style>
