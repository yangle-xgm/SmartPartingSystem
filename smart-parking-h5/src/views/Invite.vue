<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <div>
          <span class="h-hero__eyebrow">VISITOR PASS</span>
          <h1 class="h-hero__title">访客邀请</h1>
          <p class="h-hero__desc">生成核验码，访客凭码即可自动抬杆入场</p>
        </div>
      </div>
      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <b>{{ invites.length }}</b>
          <span>邀请总数</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <b>{{ validCount }}</b>
          <span>有效中</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <b>{{ usedCount }}</b>
          <span>已使用</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <!-- 生成表单 -->
      <div class="h-card">
        <div class="h-card__head">
          <span class="h-card__title">生成访客核验码</span>
          <span class="h-card__extra">最长 7 天</span>
        </div>

        <van-field
          v-model="genForm.plateNo"
          label="访客车牌"
          placeholder="如：京E88888"
          clearable
          maxlength="10"
        />

        <div class="dur">
          <span class="dur__label">有效期</span>
          <div class="dur__items">
            <button
              v-for="h in hourOptions"
              :key="h.value"
              type="button"
              class="dur__item"
              :class="{ active: genForm.expireHours === h.value }"
              @click="genForm.expireHours = h.value"
            >{{ h.label }}</button>
          </div>
        </div>

        <button class="h-btn" style="margin-top: 16px;" :disabled="generating" @click="handleCreate">
          <van-icon name="add-o" />
          {{ generating ? '生成中…' : '生成核验码' }}
        </button>
      </div>

      <!-- 新生成的核验码 -->
      <div v-if="newInvite" class="h-card ticket h-enter">
        <div class="ticket__eyebrow">核验码已生成</div>
        <div class="h-code">{{ newInvite.inviteCode }}</div>
        <div class="ticket__meta">
          {{ newInvite.plateNoMasked || newInvite.plateNo }} · 有效至 {{ shortTime(newInvite.expireTime) }}
        </div>
        <div class="ticket__actions">
          <button class="ticket__btn" type="button" @click="copyCode(newInvite.inviteCode)">
            <van-icon name="description" />复制核验码
          </button>
          <button class="ticket__btn ticket__btn--ghost" type="button" @click="newInvite = null">
            收起
          </button>
        </div>
      </div>

      <!-- 我的邀请 -->
      <div class="h-section">
        <span class="h-section__title">我的邀请记录</span>
        <span class="h-section__more" @click="loadData">刷新</span>
      </div>

      <div class="chips">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          class="chips__item"
          :class="{ active: activeFilter === f.value }"
          @click="activeFilter = f.value"
        >{{ f.label }}</button>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="loadData">
        <template v-if="visibleInvites.length">
          <div v-for="inv in visibleInvites" :key="inv.id" class="h-card inv">
            <div class="inv__head">
              <div>
                <div class="inv__code" @click="copyCode(inv.inviteCode)">
                  {{ inv.inviteCode }}
                  <van-icon name="description" />
                </div>
                <div class="inv__plate">{{ inv.plateNoMasked || inv.plateNo || '-' }}</div>
              </div>
              <span class="h-pill" :data-tone="statusTone[inv.status]">{{ statusMap[inv.status] || '未知' }}</span>
            </div>

            <div class="h-kv">
              <span class="h-kv__k">有效期至</span>
              <span class="h-kv__v">{{ shortTime(inv.expireTime) }}</span>
            </div>

            <button
              v-if="inv.status === 0"
              class="minibtn minibtn--danger"
              type="button"
              @click="handleInvalidate(inv)"
            >作废该核验码</button>
          </div>
        </template>
        <div v-else class="h-card h-empty">
          <van-icon name="friends-o" class="h-empty__icon" />
          {{ activeFilter === 0 ? '还没有邀请记录' : '该状态下暂无记录' }}
        </div>
      </van-pull-refresh>

      <p class="tip">
        提示：访客到闸口时输入 6 位核验码，或在门岗人工核验车牌，系统将自动开闸并生成访客停车订单。
      </p>
    </div>

    <TabBar />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { toastOnce } from '@/utils/request'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { createInvite, getInviteList, invalidateInvite } from '@/api/order'
import { getUserId } from '@/api/user'
import TabBar from '@/components/TabBar.vue'

const userId = getUserId()

const generating = ref(false)
const refreshing = ref(false)
const newInvite = ref(null)
const invites = ref([])
const activeFilter = ref(0)

const genForm = reactive({ plateNo: '', expireHours: 24 })
const hourOptions = [
  { label: '2 小时', value: 2 },
  { label: '6 小时', value: 6 },
  { label: '1 天', value: 24 },
  { label: '3 天', value: 72 }
]
const filters = [
  { label: '全部', value: 0 },
  { label: '有效', value: 1 },
  { label: '已使用', value: 2 },
  { label: '已失效', value: 3 }
]

const statusMap = { 0: '有效', 1: '已使用', 2: '已过期', 3: '已作废' }
const statusTone = { 0: 'teal', 1: 'muted', 2: 'warning', 3: 'danger' }

const validCount = computed(() => invites.value.filter(i => i.status === 0).length)
const usedCount = computed(() => invites.value.filter(i => i.status === 1).length)

const visibleInvites = computed(() => {
  const f = activeFilter.value
  if (f === 0) return invites.value
  if (f === 1) return invites.value.filter(i => i.status === 0)
  if (f === 2) return invites.value.filter(i => i.status === 1)
  return invites.value.filter(i => i.status === 2 || i.status === 3)
})

function shortTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(() => loadData())

async function loadData() {
  refreshing.value = true
  try {
    // 业主视角由后端按 X-User-Id 强制过滤，无需传 userId
    const res = await getInviteList({ current: 1, size: 50 })
    invites.value = res.code === 200 ? (res.data?.records || []) : []
  } catch {
    invites.value = []
  } finally {
    refreshing.value = false
  }
}

async function handleCreate() {
  const plateNo = genForm.plateNo.trim().toUpperCase()
  if (!plateNo) {
    showToast('请输入访客车牌号')
    return
  }
  if (plateNo.length < 7) {
    showToast('车牌号格式不正确')
    return
  }
  generating.value = true
  try {
    const res = await createInvite(userId, plateNo, genForm.expireHours)
    if (res.code === 200) {
      newInvite.value = res.data
      genForm.plateNo = ''
      showSuccessToast('核验码已生成')
      loadData()
    }
  } catch (e) {
    toastOnce(e, '生成失败')
  } finally {
    generating.value = false
  }
}

async function copyCode(code) {
  if (!code) return
  try {
    await navigator.clipboard.writeText(String(code))
    showSuccessToast('已复制')
  } catch {
    showToast(`核验码：${code}`)
  }
}

async function handleInvalidate(inv) {
  try {
    await showConfirmDialog({
      title: '作废核验码',
      message: `确定作废 ${inv.inviteCode} 吗？作废后访客将无法凭码入场。`
    })
  } catch {
    return
  }
  try {
    const res = await invalidateInvite(inv.id, userId)
    if (res.code === 200) {
      showSuccessToast('已作废')
      loadData()
    }
  } catch (e) {
    toastOnce(e, '操作失败')
  }
}
</script>

<style scoped>
.dur {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 0 0;
}
.dur__label { font-size: 14px; color: var(--sp-text-2); flex-shrink: 0; }
.dur__items { display: flex; gap: 6px; flex: 1; }
.dur__item {
  flex: 1;
  height: 30px;
  font-size: 12px;
  color: var(--sp-text-2);
  background: var(--sp-surface-2);
  border: 1px solid var(--sp-card-border);
  border-radius: var(--sp-radius-pill);
}
.dur__item.active {
  color: var(--sp-brand);
  font-weight: 700;
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}

/* 核验码票据 */
.ticket {
  margin-top: var(--sp-gap-card);
  text-align: center;
  color: var(--sp-hero-text);
  background: var(--sp-hero-bg);
  border: none;
  box-shadow: var(--sp-shadow-hero);
  position: relative;
  overflow: hidden;
}
.ticket::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--sp-hero-glow);
  pointer-events: none;
}
.ticket__eyebrow {
  position: relative;
  z-index: 1;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--sp-gold);
  text-transform: uppercase;
}
.ticket .h-code { position: relative; z-index: 1; margin: 8px 0 6px; }
.ticket__meta {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: var(--sp-hero-text-2);
}
.ticket__actions {
  position: relative;
  z-index: 1;
  display: flex;
  gap: var(--sp-2);
  margin-top: 14px;
}
.ticket__btn {
  flex: 1;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: #2A2214;
  background: var(--sp-grad-gold);
  border: none;
  border-radius: var(--sp-radius-pill);
}
.ticket__btn--ghost {
  flex: 0 0 76px;
  color: var(--sp-hero-text);
  background: var(--sp-hero-fill);
  border: 1px solid var(--sp-hero-line);
}

/* 状态筛选 */
.chips {
  display: flex;
  gap: 6px;
  margin-bottom: var(--sp-2);
  overflow-x: auto;
  padding-bottom: 2px;
}
.chips__item {
  flex-shrink: 0;
  height: 28px;
  padding: 0 14px;
  font-size: 12px;
  color: var(--sp-text-2);
  background: var(--sp-card);
  border: 1px solid var(--sp-card-border);
  border-radius: var(--sp-radius-pill);
}
.chips__item.active {
  color: var(--sp-brand);
  font-weight: 700;
  background: var(--sp-brand-soft);
  border-color: var(--sp-brand);
}

.inv + .inv { margin-top: var(--sp-gap-card); }
.inv__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 6px;
}
.inv__code {
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: var(--sp-font-num);
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: var(--sp-text);
}
.inv__code .van-icon { font-size: 13px; color: var(--sp-text-3); }
.inv__plate { margin-top: 2px; font-size: 12px; color: var(--sp-text-3); }

.minibtn {
  width: 100%;
  height: 34px;
  margin-top: 10px;
  font-size: 13px;
  font-weight: 600;
  border: none;
  border-radius: var(--sp-radius-btn);
}
.minibtn--danger { color: var(--sp-danger); background: var(--sp-danger-soft); }

.tip {
  margin: var(--sp-4) 2px 0;
  font-size: 11px;
  line-height: 1.7;
  color: var(--sp-text-3);
}
</style>
