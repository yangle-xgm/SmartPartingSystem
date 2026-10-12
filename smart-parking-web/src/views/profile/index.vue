<template>
  <div class="profile-page">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="avatar-block">
            <el-avatar :size="100" :src="userInfo.avatar">
              <el-icon :size="40"><UserFilled /></el-icon>
            </el-avatar>
            <el-upload
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :http-request="uploadAvatar"
              style="margin-top:10px"
            >
              <el-button size="small" type="primary" plain>更换头像</el-button>
            </el-upload>
            <h2 class="name">{{ userInfo.nickname || userInfo.username }}</h2>
            <el-tag :type="roleTagMap[userInfo.role]">{{ roleMap[userInfo.role] }}</el-tag>
          </div>
          <el-descriptions :column="1" border style="margin-top:20px">
            <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ maskPhone(userInfo.phone) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ userInfo.createTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><span>修改个人信息</span></template>
          <el-form :model="form" label-width="100px" style="max-width:500px">
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
            <el-form-item label="原密码">
              <el-input v-model="form.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="form.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="form.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-button type="primary" @click="handleSave">保存修改</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { getUserInfo } from '@/api/user'

const userInfo = ref({})
const form = reactive({ nickname: '', phone: '', oldPassword: '', newPassword: '', confirmPassword: '' })
const roleMap = { 0: '业主', 1: '物业', 2: '超管' }
const roleTagMap = { 0: '', 1: 'warning', 2: 'danger' }

const maskPhone = (p) => p ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-'

onMounted(async () => {
  try {
    const res = await getUserInfo()
    userInfo.value = res.data || {}
    form.nickname = userInfo.value.nickname || ''
    form.phone = userInfo.value.phone || ''
  } catch (e) { console.error(e) }
})

const handleSave = async () => {
  if (form.newPassword && form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次密码不一致'); return
  }
  ElMessage.success('修改成功')
  form.oldPassword = form.newPassword = form.confirmPassword = ''
}

const beforeAvatarUpload = (file) => {
  const isImg = ['image/jpeg', 'image/png', 'image/jpg'].includes(file.type)
  if (!isImg) { ElMessage.error('只支持 JPG/PNG 图片'); return false }
  if (file.size > 2 * 1024 * 1024) { ElMessage.error('图片不超过2MB'); return false }
  return true
}

const uploadAvatar = async (opt) => {
  const fd = new FormData()
  fd.append('file', opt.file)
  const res = await request({ url: '/user/upload-avatar', method: 'post', data: fd, headers: { 'Content-Type': 'multipart/form-data' } })
  userInfo.value.avatar = res.data
  ElMessage.success('头像已更新')
}
</script>

<style scoped>
.avatar-block { text-align: center; padding: 20px 0; }
.name { margin: 12px 0 8px; }
</style>
