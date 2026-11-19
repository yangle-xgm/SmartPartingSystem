<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Billing Engine"
      title="计费规则"
      desc="按车辆类型与车牌颜色匹配的计费模板。日封顶按自然日（00:00）结算，跨日订单每天独立起算额度。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增规则
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="规则总数" :value="rules.length" unit="条" icon="SetUp" tone="brand" />
      <StatCard label="已启用" :value="enabledCount" unit="条" icon="CircleCheck" tone="teal" />
      <StatCard label="临时车规则" :value="tempCount" unit="条" icon="Clock" tone="warning" hint="vehicleType = 1" />
      <StatCard label="月租车规则" :value="monthlyCount" unit="条" icon="Calendar" tone="gold" hint="vehicleType = 2" />
    </div>

    <section class="sp-card">
      <el-table :data="rules" v-loading="loading" stripe>
        <el-table-column prop="ruleName" label="规则名称" min-width="150" />
        <el-table-column label="适用类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.vehicleType]" effect="light">{{ typeText[row.vehicleType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="车牌颜色" width="100">
          <template #default="{ row }">{{ row.plateColor || '全部' }}</template>
        </el-table-column>
        <el-table-column label="免费时长" width="100">
          <template #default="{ row }">{{ row.freeMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="计费单价" width="150">
          <template #default="{ row }">
            <b class="price">¥{{ money(row.unitPrice) }}</b>
            <span class="unit">/ {{ row.unitDuration }} 分钟</span>
          </template>
        </el-table-column>
        <el-table-column label="日封顶" width="120">
          <template #default="{ row }">
            <span v-if="row.dailyCap != null">¥{{ money(row.dailyCap) }}</span>
            <span v-else class="muted">不封顶</span>
          </template>
        </el-table-column>
        <el-table-column label="周末" width="160">
          <template #default="{ row }">
            <template v-if="row.weekendUnitPrice != null">
              <b class="price">¥{{ money(row.weekendUnitPrice) }}</b>
              <span class="unit">/ {{ row.unitDuration }} 分钟</span>
            </template>
            <span v-else class="muted">同工作日</span>
          </template>
        </el-table-column>
        <el-table-column label="月租价" width="110">
          <template #default="{ row }">
            <span v-if="row.monthlyPrice != null">¥{{ money(row.monthlyPrice) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="status">
              <i class="sp-dot" :data-tone="row.status === 1 ? 'free' : 'fix'" />
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="tip">
        改了单价只影响<strong>之后创建</strong>的订单；已在场的进行中订单仍按入场时匹配的规则结算。
        删除时若仍有进行中订单引用该规则会被拒绝，避免结算算不出金额。
      </p>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑计费规则' : '新增计费规则'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" maxlength="50" placeholder="如：临时车标准费率" />
        </el-form-item>
        <el-form-item label="适用类型" prop="vehicleType">
          <el-radio-group v-model="form.vehicleType">
            <el-radio-button :value="0">全部</el-radio-button>
            <el-radio-button :value="1">临时车</el-radio-button>
            <el-radio-button :value="2">月租车</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="车牌颜色">
          <el-input v-model="form.plateColor" maxlength="10" placeholder="留空表示不限（如：绿）" />
        </el-form-item>
        <el-form-item label="免费时长" prop="freeMinutes">
          <el-input-number v-model="form.freeMinutes" :min="0" :max="1440" :step="5" />
          <span class="hint">分钟</span>
        </el-form-item>
        <el-form-item label="计费单价" prop="unitPrice">
          <el-input-number v-model="form.unitPrice" :min="0.01" :precision="2" :step="0.5" />
          <span class="hint">元 / 每</span>
          <el-input-number v-model="form.unitDuration" :min="1" :max="1440" :step="10" style="margin-left: 8px" />
          <span class="hint">分钟</span>
        </el-form-item>
        <el-form-item label="日封顶">
          <el-input-number v-model="form.dailyCap" :min="0" :precision="2" :step="10" />
          <span class="hint">元（按自然日，留空不封顶）</span>
        </el-form-item>
        <el-form-item label="周末单价">
          <el-input-number v-model="form.weekendUnitPrice" :min="0" :precision="2" :step="0.5" />
          <span class="hint">元（留空沿用工作日）</span>
        </el-form-item>
        <el-form-item label="周末封顶">
          <el-input-number v-model="form.weekendDailyCap" :min="0" :precision="2" :step="10" />
          <span class="hint">元（留空沿用工作日）</span>
        </el-form-item>
        <el-form-item label="月租价格">
          <el-input-number v-model="form.monthlyPrice" :min="0" :precision="2" :step="50" />
          <span class="hint">元/月（月租车规则必填）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getBillingRules, createBillingRule, updateBillingRule, deleteBillingRule } from '@/api/order'

const loading = ref(false)
const saving = ref(false)
const rules = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const typeText = { 0: '全部', 1: '临时车', 2: '月租车' }
const typeTag = { 0: 'info', 1: 'primary', 2: 'success' }

const money = (v) => Number(v ?? 0).toFixed(2)
const enabledCount = computed(() => rules.value.filter(r => r.status === 1).length)
const tempCount = computed(() => rules.value.filter(r => r.vehicleType === 1).length)
const monthlyCount = computed(() => rules.value.filter(r => r.vehicleType === 2).length)

const emptyForm = () => ({
  id: null,
  ruleName: '',
  vehicleType: 1,
  plateColor: '',
  freeMinutes: 30,
  unitPrice: 2.5,
  unitDuration: 60,
  dailyCap: null,
  monthlyPrice: null,
  weekendUnitPrice: null,
  weekendDailyCap: null,
  status: 1
})

const form = reactive(emptyForm())

const formRules = {
  ruleName: [{ required: true, message: '请填写规则名称', trigger: 'blur' }],
  vehicleType: [{ required: true, message: '请选择适用类型', trigger: 'change' }],
  freeMinutes: [{ required: true, message: '请填写免费时长', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请填写计费单价', trigger: 'blur' }],
  unitDuration: [{ required: true, message: '请填写计费单位', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getBillingRules()
    rules.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, emptyForm(), {
    id: row.id,
    ruleName: row.ruleName,
    vehicleType: row.vehicleType,
    plateColor: row.plateColor || '',
    freeMinutes: row.freeMinutes,
    unitPrice: Number(row.unitPrice),
    unitDuration: row.unitDuration,
    dailyCap: row.dailyCap == null ? null : Number(row.dailyCap),
    monthlyPrice: row.monthlyPrice == null ? null : Number(row.monthlyPrice),
    weekendUnitPrice: row.weekendUnitPrice == null ? null : Number(row.weekendUnitPrice),
    weekendDailyCap: row.weekendDailyCap == null ? null : Number(row.weekendDailyCap),
    status: row.status
  })
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.vehicleType === 2 && (form.monthlyPrice == null || form.monthlyPrice <= 0)) {
      ElMessage.warning('月租车规则必须填写月租价格')
      return
    }
    if (form.dailyCap != null && form.dailyCap < form.unitPrice) {
      ElMessage.warning('日封顶不能低于一个计费单位的价格')
      return
    }
    const payload = {
      ruleName: form.ruleName.trim(),
      vehicleType: form.vehicleType,
      plateColor: form.plateColor?.trim() || null,
      freeMinutes: form.freeMinutes,
      unitPrice: form.unitPrice,
      unitDuration: form.unitDuration,
      dailyCap: form.dailyCap,
      monthlyPrice: form.monthlyPrice,
      weekendUnitPrice: form.weekendUnitPrice,
      weekendDailyCap: form.weekendDailyCap,
      status: form.status
    }
    saving.value = true
    try {
      if (form.id) {
        await updateBillingRule(form.id, payload)
        ElMessage.success('规则已更新')
      } else {
        await createBillingRule(payload)
        ElMessage.success('规则已创建')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除规则「${row.ruleName}」后不可恢复，已被进行中订单引用时会拒绝删除。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteBillingRule(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.price {
  font-size: 13px;
  color: var(--sp-text);
}

.unit {
  margin-left: 2px;
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.muted {
  color: var(--sp-text-3);
  font-size: 12px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--sp-text-2);
}

.hint {
  margin-left: 6px;
  font-size: 12px;
  color: var(--sp-text-3);
}

.tip {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  font-size: 12px;
  color: var(--sp-text-2);
  line-height: 1.7;
}

.tip strong {
  color: var(--sp-text);
}
</style>
