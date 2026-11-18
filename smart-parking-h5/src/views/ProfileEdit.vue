<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="h-hero__top">
        <button class="h-hero__back" type="button" aria-label="返回" @click="goBack">
          <van-icon name="arrow-left" />
        </button>
        <div class="h-hero__lead">
          <span class="h-hero__eyebrow">PROFILE</span>
          <h1 class="h-hero__title">编辑资料</h1>
          <p class="h-hero__desc">姓名与头像会展示给物业，手机号仅用于联系</p>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-card avatar-card">
        <button class="avatar" type="button" :disabled="uploading" @click="pickFile">
          <img v-if="form.avatar" :src="form.avatar" class="avatar__img" alt="头像" />
          <span v-else class="avatar__text">{{ initial }}</span>
          <span class="avatar__mask">
            <van-icon :name="uploading ? 'replay' : 'photograph'" />
            {{ uploading ? '上传中' : '更换头像' }}
          </span>
        </button>
        <p class="avatar__hint">支持 JPG / PNG / WEBP，不超过 2MB</p>
        <input
          ref="fileInput"
          class="avatar__input"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          @change="onFileChange"
        />
      </div>

      <div class="h-card">
        <div class="h-card__head">
          <span class="h-card__title">基本信息</span>
          <span v-if="loading" class="h-card__extra">加载中…</span>
        </div>

        <label class="row">
          <span class="row__k">姓名</span>
          <input v-model="form.realName" class="row__input" maxlength="20" placeholder="请输入真实姓名" />
        </label>

        <label class="row">
          <span class="row__k">手机号</span>
          <input
            v-model="form.phone"
            class="row__input"
            type="tel"
            maxlength="11"
            :placeholder="maskedPhone ? `当前 ${maskedPhone}，不修改请留空` : '请输入 11 位手机号'"
          />
        </label>

        <div class="row row--static">
          <span class="row__k">账号</span>
          <span class="row__v">{{ userInfo.username || '—' }}</span>
        </div>
        <div class="row row--static">
          <span class="row__k">所属小区</span>
          <span class="row__v">{{ communityName || '未绑定小区' }}</span>
        </div>
      </div>

      <button class="h-btn save" type="button" :disabled="saving" @click="handleSave">
        {{ saving ? '保存中…' : '保存修改' }}
      </button>
      <p class="tip">手机号变更后用于登录与通知，请确保号码正确。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { toastOnce } from '@/utils/request'
import { getUserInfo, getUserId, getCommunityList, updateUser, uploadAvatar } from '@/api/user'

const router = useRouter()
const userId = getUserId()

const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const userInfo = ref({})
const communityName = ref('')
const maskedPhone = ref('')
const form = ref({ realName: '', phone: '', avatar: '' })
const fileInput = ref(null)

const initial = computed(() => {
  const name = form.value.realName || userInfo.value.username || '业'
  return String(name).slice(0, 1).toUpperCase()
})

function goBack() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/mine')
}

onMounted(async () => {
  const [u, c] = await Promise.allSettled([getUserInfo(userId), getCommunityList()])
  if (u.status === 'fulfilled' && u.value.code === 200) {
    userInfo.value = u.value.data || {}
    maskedPhone.value = userInfo.value.phoneMasked || ''
    // 手机号不回填：后端只给掩码，回填掩码再保存会把真实号码写成「138****8000」
    form.value.realName = userInfo.value.realName || ''
    form.value.avatar = userInfo.value.avatar || ''
  }
  if (c.status === 'fulfilled' && c.value.code === 200) {
    const list = Array.isArray(c.value.data) ? c.value.data : []
    const cid = userInfo.value.communityId
    const hit = cid != null ? list.find(x => Number(x.id) === Number(cid)) : null
    communityName.value = hit?.name || ''
  }
  loading.value = false
})

function pickFile() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  // 允许重复选择同一文件
  e.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    showToast('只支持 JPG / PNG / WEBP 图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    showToast('图片大小不超过 2MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    if (res.code === 200 && res.data) {
      form.value.avatar = res.data
      showToast('头像已上传，记得保存')
    }
  } catch (err) {
    toastOnce(err, '头像上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  const realName = form.value.realName.trim()
  const phone = form.value.phone.trim()
  if (realName.length < 2) {
    showToast('请填写真实姓名（至少 2 个字）')
    return
  }
  if (phone && !/^1\d{10}$/.test(phone)) {
    showToast('手机号格式不正确')
    return
  }
  saving.value = true
  try {
    // 不传 phone 时后端保持原值（updateById 忽略 null）
    const payload = { id: userId, realName, avatar: form.value.avatar || null }
    if (phone) payload.phone = phone
    const res = await updateUser(payload)
    if (res.code === 200) {
      showSuccessToast('资料已更新')
      setTimeout(() => goBack(), 500)
    }
  } catch (e) {
    toastOnce(e, '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.avatar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--sp-5) var(--sp-4);
}
.avatar {
  position: relative;
  width: 88px;
  height: 88px;
  padding: 0;
  overflow: hidden;
  border: none;
  border-radius: 50%;
  background: var(--sp-grad-gold);
  box-shadow: 0 10px 24px rgba(240, 201, 135, 0.3);
}
.avatar__img { width: 100%; height: 100%; object-fit: cover; }
.avatar__text {
  font-size: 34px;
  font-weight: 700;
  color: #2A2214;
}
.avatar__mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 10px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
}
.avatar__hint {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--sp-text-3);
}
.avatar__input { display: none; }

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.row + .row { border-top: 1px solid var(--sp-divider); }
.row__k {
  width: 64px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--sp-text-3);
}
.row__input {
  flex: 1;
  min-width: 0;
  padding: 0;
  font-size: 14px;
  color: var(--sp-text);
  background: transparent;
  border: none;
  outline: none;
  text-align: right;
}
.row__input::placeholder { color: var(--sp-text-3); font-size: 12px; }
.row__v { flex: 1; text-align: right; font-size: 14px; color: var(--sp-text-2); }

.save { margin-top: var(--sp-4); height: 46px; }
.tip {
  margin: var(--sp-3) 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--sp-text-3);
}
</style>
