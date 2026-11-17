<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Account"
      title="个人中心"
      desc="维护昵称、手机号与头像。手机号以 AES-256 加密存储，接口仅返回脱敏结果；修改时留空表示不修改。"
    />

    <div class="profile-grid">
      <!-- 名片 -->
      <section class="sp-card profile-card">
        <div class="avatar-wrap">
          <el-avatar :size="86" :src="userInfo.avatar" class="avatar">
            <el-icon :size="34"><UserFilled /></el-icon>
          </el-avatar>
          <el-upload
            :show-file-list="false"
            :before-upload="beforeAvatarUpload"
            :http-request="uploadAvatar"
            class="avatar-upload"
          >
            <el-button size="small" plain :loading="uploading">更换头像</el-button>
          </el-upload>
        </div>

        <h2 class="profile-name">{{ userInfo.realName || userInfo.username || '未登录' }}</h2>
        <el-tag :type="roleTagMap[userInfo.role]" effect="light" class="profile-role">{{ roleMap[userInfo.role] }}</el-tag>

        <div class="profile-kv">
          <div class="profile-kv__row">
            <span>用户名</span>
            <b>{{ userInfo.username || '—' }}</b>
          </div>
          <div class="profile-kv__row">
            <span>手机号</span>
            <b>{{ userInfo.phoneMasked || '—' }}</b>
          </div>
          <div class="profile-kv__row">
            <span>用户 ID</span>
            <b>{{ userInfo.id || '—' }}</b>
          </div>
          <div class="profile-kv__row">
            <span>注册时间</span>
            <b>{{ shortTime(userInfo.createTime) }}</b>
          </div>
        </div>

        <div class="profile-tip">
          <el-icon><Lock /></el-icon>
          <span>手机号、车牌号在数据库中均已加密存储，页面仅展示脱敏值。</span>
        </div>
      </section>

      <!-- 编辑 -->
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">修改资料</span>
          <span class="sp-card__sub">保存后立即生效</span>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="profile-form">
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" placeholder="用于通知与账单展示" maxlength="20" />
            <p v-if="nameMasked" class="form-hint">当前：{{ userInfo.realName }}（不修改请留空，留空不会覆盖原姓名）</p>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="不修改请留空" maxlength="11" />
            <p class="form-hint">当前已绑定：{{ userInfo.phoneMasked || '未绑定' }}</p>
          </el-form-item>
          <el-form-item label="头像地址">
            <el-input v-model="form.avatar" placeholder="可通过左侧上传自动填充" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="pwd-note">
          <span class="sp-feature__icon"><el-icon><Key /></el-icon></span>
          <div>
            <p class="sp-feature__title">密码修改</p>
            <p class="sp-feature__desc">
              出于安全考虑，密码重置由物业管理员在用户管理中执行（BCrypt 单向哈希，系统不保留明文）。
              如需重置请联系物业前台。
            </p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getUserInfo, updateUser, getUserId } from '@/api/user'
import request, { toastOnce } from '@/utils/request'

const userInfo = ref({})
const saving = ref(false)
const uploading = ref(false)
const formRef = ref(null)
const form = reactive({ realName: '', phone: '', avatar: '' })

const roleMap = { 0: '业主', 1: '物业管理员', 2: '超级管理员' }
const roleTagMap = { 0: 'primary', 1: 'warning', 2: 'danger' }

/**
 * 姓名脱敏兜底（E2E 报告 P2-2）：
 * /user/info 若返回「张*」这类脱敏值，不能回填输入框 —— 用户点保存会把真名覆盖成「张*」。
 * 这里遇到脱敏值就留空并给出提示，留空时保存请求不带 realName（后端 updateById 忽略 null）。
 */
const nameMasked = ref(false)
const isMaskedName = (v) => typeof v === 'string' && v.includes('*')

const rules = {
  realName: [{
    validator: (rule, value, cb) => {
      if (!value && !nameMasked.value) cb(new Error('请输入真实姓名'))
      else cb()
    },
    trigger: 'blur'
  }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const fill = (data) => {
  userInfo.value = data || {}
  nameMasked.value = isMaskedName(data?.realName)
  form.realName = nameMasked.value ? '' : (data?.realName || '')
  form.avatar = data?.avatar || ''
  form.phone = ''
}

onMounted(async () => {
  try {
    // GET /api/user/info?userId= 必传 userId
    const res = await getUserInfo()
    fill(res.data)
  } catch (e) {
    toastOnce(e, '加载用户信息失败')
  }
})

const resetForm = () => {
  form.realName = nameMasked.value ? '' : (userInfo.value.realName || '')
  form.phone = ''
  form.avatar = userInfo.value.avatar || ''
}

const handleSave = async () => {
  const f = formRef.value
  if (!f) return
  await f.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      // PUT /api/user/update，body 为 UserUpdateDTO{id, phone, realName, avatar}
      // realName 为空表示「不修改」，不传该字段，避免把已脱敏的值写回库里
      await updateUser({
        id: getUserId(),
        avatar: form.avatar,
        ...(form.realName.trim() ? { realName: form.realName.trim() } : {}),
        ...(form.phone ? { phone: form.phone } : {})
      })
      ElMessage.success('资料已更新')
      const res = await getUserInfo()
      fill(res.data)
    } catch (e) {
      toastOnce(e, '保存失败')
    } finally {
      saving.value = false
    }
  })
}

const beforeAvatarUpload = (file) => {
  const ok = ['image/jpeg', 'image/png', 'image/jpg'].includes(file.type)
  if (!ok) {
    ElMessage.error('只支持 JPG / PNG 图片')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不超过 2MB')
    return false
  }
  return true
}

const uploadAvatar = async (opt) => {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', opt.file)
    // POST /api/user/upload-avatar（multipart/form-data），返回头像相对路径
    const res = await request({
      url: '/user/upload-avatar',
      method: 'post',
      data: fd,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const url = res.data
    // 上传成功后同步写入用户资料，避免只改本地状态
    await updateUser({ id: getUserId(), avatar: url })
    userInfo.value.avatar = url
    form.avatar = url
    ElMessage.success('头像已更新')
  } catch (e) {
    toastOnce(e, '头像上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: var(--sp-4);
  align-items: start;
}

.profile-card {
  text-align: center;
}

.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding-top: 6px;
}

.avatar {
  background: var(--sp-grad-brand);
  color: #fff;
  font-size: 30px;
  border: 3px solid var(--sp-surface-2);
}

.profile-name {
  margin: 12px 0 6px;
  font-size: 19px;
  font-weight: 800;
  color: var(--sp-text);
}

.profile-role {
  margin-bottom: 16px;
}

.profile-kv {
  border-top: 1px dashed var(--sp-divider);
  padding-top: 12px;
  text-align: left;
}

.profile-kv__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  font-size: 12.5px;
}

.profile-kv__row span {
  color: var(--sp-text-3);
}

.profile-kv__row b {
  color: var(--sp-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.profile-tip {
  margin-top: 14px;
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--sp-surface-2);
  text-align: left;
  font-size: 11.5px;
  color: var(--sp-text-3);
  line-height: 1.6;
}

.profile-tip .el-icon {
  color: var(--sp-teal);
  margin-top: 2px;
}

.profile-form {
  max-width: 520px;
}

.form-hint {
  font-size: 11.5px;
  color: var(--sp-text-3);
  margin-top: 4px;
}

.pwd-note {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  padding: 14px;
  border-radius: 12px;
  border: 1px dashed var(--sp-card-border);
  background: var(--sp-surface-2);
}

@media (max-width: 1000px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
