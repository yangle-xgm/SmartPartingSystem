<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <div>
          <span class="h-hero__eyebrow">MY VEHICLES</span>
          <h1 class="h-hero__title">我的车辆</h1>
          <p class="h-hero__desc">绑定车牌后即可无感进出闸机、在线缴费</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <b>{{ vehicles.length }}</b>
          <span>绑定车辆</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <b>{{ normalCount }}</b>
          <span>状态正常</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <b>{{ monthlyCount }}</b>
          <span>月卡车辆</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-section">
        <span class="h-section__title">车辆列表</span>
        <span class="h-section__more" @click="loadData">刷新</span>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="vehicles.length">
          <div v-for="v in vehicles" :key="v.id" class="h-card car">
            <div class="car__head">
              <span class="h-plate" :data-color="v.plateColor || '蓝'">{{ v.plateNoMasked || v.plateNo }}</span>
              <span class="h-pill" :data-tone="v.status === 0 ? 'teal' : 'danger'">
                {{ v.status === 0 ? '正常' : '黑名单' }}
              </span>
            </div>

            <div class="car__meta">
              <span>{{ v.brand || '未填写品牌' }}</span>
              <span class="dot">·</span>
              <span>{{ v.color || '未填写颜色' }}</span>
              <span class="dot">·</span>
              <span>{{ typeMap[v.type] || '私家车' }}</span>
            </div>

            <div class="h-kv">
              <span class="h-kv__k">月卡有效期</span>
              <span class="h-kv__v" :class="{ warn: soon(v.monthlyExpire) }">
                {{ v.monthlyExpire ? String(v.monthlyExpire).replace('T', ' ').slice(0, 10) : '未开通月卡' }}
              </span>
            </div>

            <div class="car__actions">
              <button class="minibtn" type="button" @click="showDetail(v)">详情</button>
              <button class="minibtn minibtn--danger" type="button" @click="handleUnbind(v)">解绑</button>
            </div>
          </div>
        </template>
        <div v-else class="h-card h-empty">
          <van-icon name="logistics" class="h-empty__icon" />
          还没有绑定车辆，点击下方按钮添加
        </div>
      </van-pull-refresh>
    </div>

    <button class="h-fab" type="button" @click="openBind">
      <van-icon name="plus" />绑定车辆
    </button>

    <!-- 绑定车辆弹层 -->
    <van-popup v-model:show="bindVisible" position="bottom" round :style="{ padding: '18px 16px 24px' }">
      <div class="sheet__title">绑定新车牌</div>
      <p class="sheet__desc">绑定后该车牌进出小区闸机将自动识别放行</p>

      <van-cell-group :border="false">
        <van-field v-model="bindForm.plateNo" label="车牌号" placeholder="如：京A88888" clearable maxlength="10" />
        <van-field v-model="bindForm.brand" label="品牌" placeholder="如：特斯拉" clearable />
        <van-field v-model="bindForm.color" label="颜色" placeholder="如：白色" clearable />
        <van-field label="车牌颜色">
          <template #input>
            <van-radio-group v-model="bindForm.plateColor" direction="horizontal">
              <van-radio name="蓝">蓝牌</van-radio>
              <van-radio name="绿">绿牌</van-radio>
              <van-radio name="黄">黄牌</van-radio>
            </van-radio-group>
          </template>
        </van-field>
      </van-cell-group>

      <button class="h-btn" style="margin-top: 18px;" :disabled="binding" @click="handleBind">
        {{ binding ? '提交中…' : '确认绑定' }}
      </button>
    </van-popup>

    <!-- 车辆详情 -->
    <van-popup v-model:show="detailVisible" position="bottom" round :style="{ padding: '18px 16px 24px' }">
      <div class="sheet__title">车辆详情</div>
      <div v-if="current" class="detail">
        <span class="h-plate" :data-color="current.plateColor || '蓝'">{{ current.plateNoMasked || current.plateNo }}</span>
        <div class="h-kv"><span class="h-kv__k">车牌颜色</span><span class="h-kv__v">{{ current.plateColor || '蓝' }}</span></div>
        <div class="h-kv"><span class="h-kv__k">车辆类型</span><span class="h-kv__v">{{ typeMap[current.type] || '私家车' }}</span></div>
        <div class="h-kv"><span class="h-kv__k">品牌 / 颜色</span><span class="h-kv__v">{{ current.brand || '-' }} / {{ current.color || '-' }}</span></div>
        <div class="h-kv"><span class="h-kv__k">状态</span><span class="h-kv__v">{{ current.status === 0 ? '正常' : '异常' }}</span></div>
        <div class="h-kv"><span class="h-kv__k">月卡到期</span><span class="h-kv__v">{{ current.monthlyExpire || '未开通' }}</span></div>
        <div class="h-kv"><span class="h-kv__k">绑定时间</span><span class="h-kv__v">{{ current.createTime || '-' }}</span></div>
      </div>
    </van-popup>

    <TabBar />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { toastOnce } from '@/utils/request'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { getVehicleList, bindVehicle, unbindVehicle } from '@/api/vehicle'
import { getUserId } from '@/api/user'
import TabBar from '@/components/TabBar.vue'

const userId = getUserId()

const vehicles = ref([])
const refreshing = ref(false)
const binding = ref(false)
const bindVisible = ref(false)
const detailVisible = ref(false)
const current = ref(null)

const bindForm = reactive({ plateNo: '', plateColor: '蓝', brand: '', color: '' })
const typeMap = { 0: '私家车', 1: '访客车辆' }

const normalCount = computed(() => vehicles.value.filter(v => v.status === 0).length)
const monthlyCount = computed(() => vehicles.value.filter(v => !!v.monthlyExpire).length)

// 30 天内到期高亮
function soon(date) {
  if (!date) return false
  const t = new Date(String(date).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return false
  return t - Date.now() < 30 * 24 * 3600 * 1000
}

onMounted(() => loadData())

async function loadData() {
  refreshing.value = true
  try {
    // 后端 /api/vehicle/list?userId= 返回 List<Vehicle>（非分页）
    const res = await getVehicleList(userId)
    if (res.code === 200) vehicles.value = Array.isArray(res.data) ? res.data : []
  } catch {
    vehicles.value = []
  } finally {
    refreshing.value = false
  }
}

function openBind() {
  bindForm.plateNo = ''
  bindForm.brand = ''
  bindForm.color = ''
  bindForm.plateColor = '蓝'
  bindVisible.value = true
}

function showDetail(v) {
  current.value = v
  detailVisible.value = true
}

const handleBind = async () => {
  const plateNo = bindForm.plateNo.trim().toUpperCase()
  if (!plateNo) {
    showToast('请输入车牌号')
    return
  }
  if (plateNo.length < 7) {
    showToast('车牌号格式不正确')
    return
  }
  binding.value = true
  try {
    const res = await bindVehicle({
      plateNo,
      plateColor: bindForm.plateColor,
      brand: bindForm.brand.trim(),
      color: bindForm.color.trim(),
      ownerId: userId,
      type: 0,
      status: 0
    })
    if (res.code === 200) {
      showSuccessToast('绑定成功')
      bindVisible.value = false
      loadData()
    }
  } catch (e) {
    toastOnce(e, '绑定失败')
  } finally {
    binding.value = false
  }
}

const handleUnbind = async (v) => {
  try {
    await showConfirmDialog({
      title: '解绑车辆',
      message: `确定解绑 ${v.plateNoMasked || v.plateNo} 吗？解绑后将无法识别入场。`
    })
  } catch {
    return
  }
  try {
    const res = await unbindVehicle(v.id, userId)
    if (res.code === 200) {
      showSuccessToast('已解绑')
      loadData()
    }
  } catch (e) {
    toastOnce(e, '解绑失败')
  }
}
</script>

<style scoped>
.car__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.car__meta {
  margin: 10px 0 8px;
  font-size: 12px;
  color: var(--sp-text-2);
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot { color: var(--sp-text-3); }

.warn { color: var(--sp-warning); font-weight: 600; }

.car__actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}
.minibtn {
  flex: 1;
  height: 34px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
  border: none;
  border-radius: var(--sp-radius-btn);
}
.minibtn:active { opacity: 0.75; }
.minibtn--danger { color: var(--sp-danger); background: var(--sp-danger-soft); }

.sheet__title { font-size: 16px; font-weight: 700; }
.sheet__desc {
  margin: 6px 0 14px;
  font-size: 12px;
  color: var(--sp-text-3);
}
.detail { padding-top: 12px; }
.detail .h-plate { margin-bottom: 10px; }
</style>
