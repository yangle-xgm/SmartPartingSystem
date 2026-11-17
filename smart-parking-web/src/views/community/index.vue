<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Communities"
      title="小区管理"
      desc="多小区隔离的基础档案：车位、用户、订单均按 community_id 隔离，超管可查看全部小区运营概览。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="小区总数" :value="list.length" unit="个" icon="OfficeBuilding" tone="brand" hint="统一在智慧星河平台运营" />
      <StatCard label="运营中" :value="operating" unit="个" icon="CircleCheck" tone="teal" hint="状态正常" />
      <StatCard label="已停用" :value="list.length - operating" unit="个" icon="CircleClose" tone="danger" hint="暂停对外服务" />
      <StatCard label="车位总量" :value="totalSpaces" unit="个" icon="Location" tone="gold" hint="各小区实际车位数合计" />
    </div>

    <div v-if="list.length" class="community-grid" v-loading="loading">
      <article v-for="c in list" :key="c.id" class="community-card">
        <div class="community-card__head">
          <span class="community-card__icon"><el-icon><OfficeBuilding /></el-icon></span>
          <div class="community-card__title">
            <b>{{ c.name }}</b>
            <span>{{ c.city || '—' }} · {{ c.address || '地址未维护' }}</span>
          </div>
          <el-tag :type="c.status === 1 ? 'success' : 'info'" effect="light">
            {{ c.status === 1 ? '运营中' : '已停用' }}
          </el-tag>
        </div>
        <div class="community-card__body">
          <div class="community-metric">
            <span>实际车位</span>
            <b class="sp-num">{{ c.actualSpaces ?? '—' }}</b>
            <em v-if="c.spacesTotal" class="community-metric__hint">规划 {{ c.spacesTotal }}</em>
          </div>
          <div class="community-metric">
            <span>小区编号</span>
            <b class="sp-num">#{{ c.id }}</b>
          </div>
          <div class="community-metric">
            <span>创建时间</span>
            <b>{{ shortDate(c.createTime) }}</b>
          </div>
        </div>
      </article>
    </div>

    <section v-else class="sp-card">
      <div class="sp-empty">
        <span class="sp-empty__star">✦</span>
        <span class="sp-empty__text">{{ loading ? '加载中…' : '暂无小区档案' }}</span>
      </div>
    </section>

    <section class="sp-card">
      <div class="sp-card__head">
        <span class="sp-card__title">档案维护说明</span>
      </div>
      <div class="sp-feature-strip">
        <div class="sp-feature">
          <span class="sp-feature__icon"><el-icon><Document /></el-icon></span>
          <div>
            <p class="sp-feature__title">数据来源</p>
            <p class="sp-feature__desc">小区档案由初始化脚本 mapper/data.sql 维护，保证跨服务一致性</p>
          </div>
        </div>
        <div class="sp-feature">
          <span class="sp-feature__icon"><el-icon><Share /></el-icon></span>
          <div>
            <p class="sp-feature__title">数据隔离</p>
            <p class="sp-feature__desc">车位与订单查询自动按网关透传的 X-Community-Id 过滤</p>
          </div>
        </div>
        <div class="sp-feature">
          <span class="sp-feature__icon"><el-icon><Lock /></el-icon></span>
          <div>
            <p class="sp-feature__title">权限边界</p>
            <p class="sp-feature__desc">仅超管（role=2）可查看全部小区数据</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getSpaceList } from '@/api/parking'
import request from '@/utils/request'

const loading = ref(false)
const list = ref([])

const operating = computed(() => list.value.filter(c => c.status === 1).length)
// D12：车位总量改用车位表实计数，规划值只作参考展示
const totalSpaces = computed(() => list.value.reduce((s, c) => s + Number(c.actualSpaces || 0), 0))

const shortDate = (t) => (t ? String(t).replace('T', ' ').slice(0, 10) : '—')

onMounted(() => loadData())

const loadData = async () => {
  loading.value = true
  try {
    // GET /api/user/community/list → List<Map>（已做驼峰别名：spacesTotal / createTime）
    const res = await request({ url: '/user/community/list', method: 'get' })
    const raw = Array.isArray(res.data) ? res.data : []
    // 兼容历史下划线返回，避免后端未升级时页面字段为空
    const base = raw.map(c => ({
      ...c,
      spacesTotal: c.spacesTotal ?? c.spaces_total ?? 0,
      createTime: c.createTime ?? c.create_time ?? null
    }))
    /**
     * D12：community.spaces_total 是建库时的规划值（200/150/100），与车位表实际 87 个对不上，
     * 页面却标成"车位总数"，用户会以为数据错了。这里按小区向车位服务取真实 total，
     * 规划值改为副标题展示；取不到时显示 — 而不是拿规划值冒充实际值。
     */
    list.value = await Promise.all(base.map(async (c) => {
      try {
        const r = await getSpaceList({ current: 1, size: 1, communityId: c.id })
        return { ...c, actualSpaces: Number(r.data?.total ?? 0) }
      } catch (e) {
        return { ...c, actualSpaces: null }
      }
    }))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.community-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--sp-4);
}

.community-card {
  padding: 18px;
  border-radius: var(--sp-radius-card);
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  transition: transform var(--sp-dur-comp) var(--sp-ease), box-shadow var(--sp-dur-comp) var(--sp-ease);
}

.community-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--sp-shadow-hover);
}

.community-card__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.community-card__icon {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--sp-brand-soft);
  color: var(--sp-brand);
  font-size: 17px;
}

.community-card__title {
  flex: 1;
  min-width: 0;
}

.community-card__title b {
  display: block;
  font-size: 14.5px;
  color: var(--sp-text);
}

.community-card__title span {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--sp-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-card__body {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--sp-divider);
}

.community-metric span {
  display: block;
  font-size: 11px;
  color: var(--sp-text-3);
}

.community-metric b {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  color: var(--sp-text);
  font-weight: 700;
}

.community-metric__hint {
  display: inline-block;
  margin-top: 2px;
  font-size: 10.5px;
  font-style: normal;
  color: var(--sp-text-3);
}
</style>
