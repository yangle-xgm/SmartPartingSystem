<template>
  <div class="page">
    <van-nav-bar title="通知中心" fixed placeholder>
      <template #right>
        <span class="mark-all" @click="handleMarkAll">全部已读</span>
      </template>
    </van-nav-bar>

    <div class="page-content">
      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="list.length">
          <div
            v-for="n in list"
            :key="n.id"
            class="notify-card"
            :class="{ unread: n.isRead === 0 }"
            @click="handleRead(n)"
          >
            <div class="notify-head">
              <span class="dot" v-if="n.isRead === 0"></span>
              <span class="title">{{ n.title }}</span>
              <span class="time">{{ n.createTime }}</span>
            </div>
            <div class="content">{{ n.content }}</div>
          </div>
        </template>
        <van-empty v-else description="暂无通知" />
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getNotifyList, markNotifyRead, markAllNotifyRead } from '@/api/notify'
import { getUserId } from '@/api/user'

const userId = getUserId()
const list = ref([])
const refreshing = ref(false)

onMounted(loadData)

async function loadData() {
  refreshing.value = true
  try {
    const res = await getNotifyList({ userId, current: 1, size: 50 })
    if (res.code === 200) list.value = res.data.records || []
  } catch {
    list.value = []
  } finally {
    refreshing.value = false
  }
}

const handleRead = async (n) => {
  if (n.isRead === 0) {
    try {
      await markNotifyRead(n.id)
      n.isRead = 1
    } catch { /* ignore */ }
  }
}

const handleMarkAll = async () => {
  try {
    const res = await markAllNotifyRead(userId)
    if (res.code === 200) {
      list.value.forEach(n => { n.isRead = 1 })
    }
  } catch { /* ignore */ }
}
</script>

<style scoped>
.mark-all {
  font-size: 13px;
  color: #1989fa;
}

.notify-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
}

.notify-card.unread {
  background: #f0f7ff;
  border: 1px solid #c8e0ff;
}

.notify-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ee0a24;
  flex-shrink: 0;
}

.title {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}

.time {
  font-size: 12px;
  color: #969799;
  flex-shrink: 0;
}

.content {
  font-size: 13px;
  color: #646566;
  margin-top: 8px;
  line-height: 1.6;
}
</style>
