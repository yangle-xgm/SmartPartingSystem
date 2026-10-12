<template>
  <div class="page">
    <van-nav-bar title="我的车辆" fixed placeholder />

    <div class="page-content">
      <!-- 绑定车辆 -->
      <van-cell-group inset>
        <van-field v-model="bindForm.plateNo" label="车牌号" placeholder="如：京A88888" clearable />
        <van-field v-model="bindForm.brand" label="品牌" placeholder="如：特斯拉" clearable />
        <van-field v-model="bindForm.color" label="颜色" placeholder="如：白色" clearable />
        <van-field name="plateColor" label="车牌颜色">
          <template #input>
            <van-radio-group v-model="bindForm.plateColor" direction="horizontal">
              <van-radio name="蓝">蓝牌</van-radio>
              <van-radio name="绿">绿牌</van-radio>
            </van-radio-group>
          </template>
        </van-field>
      </van-cell-group>
      <div class="btn-area">
        <van-button type="primary" block round :loading="binding" @click="handleBind">绑定车辆</van-button>
      </div>

      <!-- 车辆列表 -->
      <van-cell-group inset title="已绑定车辆" style="margin-top: 16px;">
        <template v-if="vehicles.length">
          <van-cell
            v-for="v in vehicles"
            :key="v.id"
            :title="v.plateNoMasked || v.plateNo"
            :label="`${v.brand || '未知品牌'} · ${v.color || ''} · ${typeMap[v.type] || ''}`"
          >
            <template #value>
              <van-tag :type="v.status === 0 ? 'success' : 'danger'">
                {{ v.status === 0 ? '正常' : '异常' }}
              </van-tag>
              <van-button size="mini" plain type="danger" style="margin-left: 8px;" @click="handleUnbind(v)">
                解绑
              </van-button>
            </template>
          </van-cell>
        </template>
        <van-empty v-else description="暂无车辆，先绑定一辆吧" :image-size="80" />
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
import { getVehicleList, bindVehicle, unbindVehicle } from '@/api/vehicle'
import { getUserId } from '@/api/user'

const userId = getUserId()
const vehicles = ref([])
const binding = ref(false)

const bindForm = reactive({ plateNo: '', plateColor: '蓝', brand: '', color: '' })
const typeMap = { 0: '私家车', 1: '访客车辆' }

onMounted(loadData)

async function loadData() {
  try {
    const res = await getVehicleList({ userId, current: 1, size: 50 })
    if (res.code === 200) vehicles.value = res.data.records || []
  } catch { /* ignore */ }
}

const handleBind = async () => {
  if (!bindForm.plateNo.trim()) {
    showToast('请输入车牌号')
    return
  }
  binding.value = true
  try {
    const res = await bindVehicle({ ...bindForm, ownerId: userId, type: 0, status: 0 })
    if (res.code === 200) {
      showSuccessToast('绑定成功')
      bindForm.plateNo = ''
      bindForm.brand = ''
      bindForm.color = ''
      loadData()
    }
  } catch (e) {
    showToast(e.message || '绑定失败')
  } finally {
    binding.value = false
  }
}

const handleUnbind = async (v) => {
  await showConfirmDialog({
    title: '解绑车辆',
    message: `确定解绑 ${v.plateNoMasked || v.plateNo} 吗？`
  })
  try {
    const res = await unbindVehicle(v.id, userId)
    if (res.code === 200) {
      showSuccessToast('已解绑')
      loadData()
    }
  } catch (e) {
    showToast(e.message || '解绑失败')
  }
}
</script>

<style scoped>
.btn-area {
  padding: 12px 24px 0;
}
</style>
