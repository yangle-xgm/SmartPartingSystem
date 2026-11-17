<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Visitor"
      title="访客邀请"
      desc="登记访客车牌后，摄像头识别到该车牌自动放行，无需访客操作；有效期内可多次进出，到期自动失效。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="有效访客" :value="stats.active" unit="个" icon="CircleCheck" tone="teal" hint="白名单内的车牌" />
      <StatCard label="已入场过" :value="stats.used" unit="个" icon="Van" tone="brand" hint="可重复进出" />
      <StatCard label="已失效" :value="stats.expired" unit="个" icon="Clock" tone="warning" hint="过期或已作废" />
      <StatCard label="同时上限" value="5" unit="个" icon="Warning" tone="gold" hint="进行中邀请数量上限" />
    </div>

    <div class="invite-grid">
      <!-- 登记表单 -->
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">登记访客车辆</span>
          <span class="sp-card__sub">生成核验码，供门岗人工核验</span>
        </div>

        <el-form label-position="top" class="invite-form">
          <el-form-item label="访客车牌号">
            <el-input v-model="genForm.plateNo" placeholder="如 湘A88888" maxlength="10" size="large" />
          </el-form-item>
          <el-form-item label="有效期">
            <div class="dur">
              <button
                v-for="h in [6, 12, 24, 72]"
                :key="h"
                type="button"
                class="dur__btn"
                :class="{ 'is-active': genForm.expireHours === h }"
                @click="genForm.expireHours = h"
              >
                {{ h >= 24 ? h / 24 + ' 天' : h + ' 小时' }}
              </button>
            </div>
          </el-form-item>
          <el-button type="primary" size="large" class="invite-submit" :loading="generating" @click="handleCreate">
            添加访客车辆
          </el-button>
        </el-form>

        <div v-if="newInvite" class="invite-result">
          <p class="invite-result__label">已登记 {{ newInvite.plateNoMasked }}，可复制核验码给访客</p>
          <div class="sp-code">{{ newInvite.inviteCode }}</div>
          <p class="invite-result__expire">有效期至 {{ shortTime(newInvite.expireTime) }}</p>
          <el-button size="small" type="primary" plain @click="copyCode(newInvite.inviteCode)">
            <el-icon><CopyDocument /></el-icon>复制核验码
          </el-button>
        </div>

        <div class="invite-note">
          <span class="sp-feature__icon"><el-icon><InfoFilled /></el-icon></span>
          <p>
            访客车辆入场时摄像头自动识别车牌并放行；未登记车牌的访客可凭核验码在门岗人工核验入场。
            同一业主进行中的邀请最多 5 个。
          </p>
        </div>
      </section>

      <!-- 列表 -->
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">我的访客车辆</span>
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button :value="''">全部</el-radio-button>
            <el-radio-button :value="0">有效</el-radio-button>
            <el-radio-button :value="2">已失效</el-radio-button>
            <el-radio-button :value="3">已作废</el-radio-button>
          </el-radio-group>
        </div>

        <el-table :data="filtered" v-loading="loading" stripe max-height="430">
          <el-table-column label="核验码" width="110">
            <template #default="{ row }">
              <button class="code-btn" @click="copyCode(row.inviteCode)" title="点击复制">
                {{ row.inviteCode }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="访客车牌" width="130">
            <template #default="{ row }">
              <span class="sp-plate">{{ row.plateNoMasked }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusTag[effStatus(row)]" effect="light">{{ statusMap[effStatus(row)] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到期时间" width="150">
            <template #default="{ row }">{{ shortTime(row.expireTime) }}</template>
          </el-table-column>
          <el-table-column label="最近入场" width="150">
            <template #default="{ row }">{{ shortTime(row.usedTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="86" fixed="right">
            <template #default="{ row }">
              <el-button v-if="effStatus(row) === 0" size="small" text type="danger" @click="handleInvalidate(row)">作废</el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="sp-empty">
              <span class="sp-empty__star">✦</span>
              <span class="sp-empty__text">还没有访客记录</span>
            </div>
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
          style="margin-top: 14px"
        />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { toastOnce } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { createInvite, getInviteList, invalidateInvite } from '@/api/order'
import { getUserId } from '@/api/user'

const loading = ref(false)
const generating = ref(false)
const tableData = ref([])
const newInvite = ref(null)
const statusFilter = ref('')

const genForm = ref({ plateNo: '', expireHours: 24 })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const statusMap = { 0: '有效', 1: '已使用', 2: '已过期', 3: '已作废' }
const statusTag = { 0: 'success', 1: 'primary', 2: 'warning', 3: 'info' }

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

/**
 * 后端 status 是「惰性更新」：只有访客尝试入场时才把过期记录置 2，
 * 所以列表里 status=0 但 expireTime 已过的记录必须由前端判定为过期，
 * 否则会出现「汇总区算成已失效、行内标签却显示有效」的自相矛盾（E2E 报告 P1-8）。
 */
const isExpired = (row) => {
  if (row.status === 2 || row.status === 3) return true
  if (!row.expireTime) return false
  const t = new Date(String(row.expireTime).replace(' ', 'T')).getTime()
  return Number.isFinite(t) && t < Date.now()
}

/** 展示/筛选统一使用「生效状态」 */
const effStatus = (row) => (row.status === 0 && isExpired(row) ? 2 : row.status)

const stats = computed(() => {
  const s = { active: 0, used: 0, expired: 0 }
  tableData.value.forEach(i => {
    const st = effStatus(i)
    if (i.status === 1 || i.usedTime) s.used++
    if (st === 2 || st === 3) s.expired++
    else if (st === 0) s.active++
  })
  return s
})

const filtered = computed(() =>
  statusFilter.value === '' ? tableData.value : tableData.value.filter(i => effStatus(i) === statusFilter.value)
)

onMounted(() => loadData())

const loadData = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  try {
    // 后端对 role=0 会强制用 X-User-Id 覆盖 userId
    const res = await getInviteList({ current: pagination.current, size: pagination.size, userId })
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!genForm.value.plateNo.trim()) {
    ElMessage.warning('请输入访客车牌号')
    return
  }
  generating.value = true
  try {
    const res = await createInvite(getUserId(), genForm.value.plateNo.trim(), genForm.value.expireHours)
    newInvite.value = res.data || null
    genForm.value.plateNo = ''
    ElMessage.success('访客车辆已登记')
    pagination.current = 1
    loadData()
  } catch (e) {
    toastOnce(e, '登记失败')
  } finally {
    generating.value = false
  }
}

const copyCode = async (code) => {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('核验码已复制')
  } catch (e) {
    ElMessage.info(`核验码：${code}`)
  }
}

const handleInvalidate = async (row) => {
  try {
    await ElMessageBox.confirm(`作废后访客将无法再入场，确定作废核验码 ${row.inviteCode}？`, '作废邀请', {
      type: 'warning',
      confirmButtonText: '作废',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await invalidateInvite(row.id, getUserId())
  ElMessage.success('已作废')
  loadData()
}
</script>

<style scoped>
.invite-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: var(--sp-4);
  align-items: start;
}

.invite-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  color: var(--sp-text-2);
  padding-bottom: 4px;
}

.dur {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.dur__btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  color: var(--sp-text-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.dur__btn:hover {
  border-color: var(--sp-brand);
  color: var(--sp-brand);
}

.dur__btn.is-active {
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
  color: var(--sp-brand);
  font-weight: 600;
}

.invite-submit {
  width: 100%;
  margin-top: 4px;
}

.invite-result {
  margin-top: 18px;
  padding: 16px;
  border-radius: 14px;
  background: var(--sp-gold-soft);
  border: 1px dashed var(--sp-gold);
  text-align: center;
}

.invite-result__label {
  font-size: 12px;
  color: var(--sp-text-2);
  margin-bottom: 10px;
}

.invite-result__expire {
  font-size: 11.5px;
  color: var(--sp-text-3);
  margin: 8px 0 10px;
}

.invite-note {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: var(--sp-surface-2);
}

.invite-note p {
  font-size: 11.5px;
  color: var(--sp-text-3);
  line-height: 1.6;
}

.code-btn {
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
  border: none;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
}

.code-btn:hover {
  background: var(--sp-brand);
  color: #fff;
}

.muted {
  color: var(--sp-text-3);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .invite-grid {
    grid-template-columns: 1fr;
  }
}
</style>
