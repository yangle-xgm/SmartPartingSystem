<template>
  <div class="page">
    <van-nav-bar title="访客邀请" fixed placeholder />

    <div class="page-content">
      <!-- 生成邀请码 -->
      <van-cell-group inset title="生成访客邀请码">
        <van-field v-model="genForm.plateNo" label="访客车牌" placeholder="如：京E88888" clearable maxlength="10" />
        <van-field name="expireHours" label="有效期">
          <template #input>
            <van-stepper v-model="genForm.expireHours" min="1" max="168" integer />
            <span class="unit">小时</span>
          </template>
        </van-field>
      </van-cell-group>
      <div class="btn-area">
        <van-button type="primary" block round :loading="generating" @click="handleCreate">
          生成邀请码
        </van-button>
      </div>

      <!-- 新生成的邀请码 -->
      <div v-if="newInvite" class="new-invite">
        <div class="invite-title">邀请码已生成，请发给访客</div>
        <div class="invite-code">{{ newInvite.inviteCode }}</div>
        <div class="invite-meta">
          {{ newInvite.plateNoMasked }} ｜ {{ newInvite.expireTime }} 前有效
        </div>
        <van-button size="small" round plain type="primary" @click="copyCode(newInvite.inviteCode)">
          复制邀请码
        </van-button>
      </div>

      <!-- 邀请列表 -->
      <van-cell-group inset title="我的邀请" style="margin-top: 16px;">
        <template v-if="invites.length">
          <van-cell
            v-for="inv in invites"
            :key="inv.id"
            :title="inv.inviteCode"
            :label="`${inv.plateNoMasked} ｜ ${inv.expireTime || '-'}`"
          >
            <template #value>
              <van-tag :type="statusTagMap[inv.status]">{{ statusMap[inv.status] }}</van-tag>
              <van-button
                v-if="inv.status === 0"
                size="mini"
                plain
                type="danger"
                style="margin-left: 8px;"
                @click="handleInvalidate(inv)"
              >作废</van-button>
            </template>
          </van-cell>
        </template>
        <van-empty v-else description="暂无邀请记录" :image-size="80" />
      </van-cell-group>
    </div>

    <van-tabbar route>
      <van-tabbar-item replace to="/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/vehicles" icon="van">车辆</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/invite" icon="friends-o">访客</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { createInvite, getInviteList, invalidateInvite } from '@/api/order'
import { getUserId } from '@/api/user'

const userId = getUserId()
const generating = ref(false)
const newInvite = ref(null)
const invites = ref([])

const genForm = reactive({ plateNo: '', expireHours: 24 })
const statusMap = { 0: '有效', 1: '已使用', 2: '已过期', 3: '已作废' }
const statusTagMap = { 0: 'success', 1: 'default', 2: 'danger', 3: 'warning' }

onMounted(loadData)

async function loadData() {
  try {
    const res = await getInviteList({ userId, current: 1, size: 50 })
    if (res.code === 200) invites.value = res.data.records || []
  } catch { /* ignore */ }
}

const handleCreate = async () => {
  if (!genForm.plateNo.trim()) {
    showToast('请输入访客车牌号')
    return
  }
  generating.value = true
  try {
    const res = await createInvite(userId, genForm.plateNo.trim(), genForm.expireHours)
    if (res.code === 200) {
      newInvite.value = res.data
      genForm.plateNo = ''
      loadData()
    }
  } catch (e) {
    showToast(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const copyCode = async (code) => {
  try {
    await navigator.clipboard.writeText(code)
    showSuccessToast('已复制')
  } catch {
    showToast(`邀请码：${code}`)
  }
}

const handleInvalidate = async (inv) => {
  await showConfirmDialog({
    title: '作废邀请码',
    message: `确定作废 ${inv.inviteCode} 吗？`
  })
  try {
    const res = await invalidateInvite(inv.id, userId)
    if (res.code === 200) {
      showSuccessToast('已作废')
      loadData()
    }
  } catch (e) {
    showToast(e.message || '操作失败')
  }
}
</script>

<style scoped>
.btn-area {
  padding: 12px 24px 0;
}

.unit {
  font-size: 13px;
  color: #969799;
  margin-left: 8px;
}

.new-invite {
  margin: 16px;
  background: linear-gradient(135deg, #1989fa, #2a6ef2);
  border-radius: 12px;
  color: #fff;
  text-align: center;
  padding: 20px 16px;
}

.invite-title {
  font-size: 14px;
  opacity: 0.9;
}

.invite-code {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 6px;
  margin: 10px 0 6px;
}

.invite-meta {
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 12px;
}
</style>
