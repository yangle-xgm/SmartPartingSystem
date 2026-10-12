<template>
  <div>
    <div class="ai-fab" @click="visible = !visible" title="AI智能客服">
      <svg v-if="!visible" viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
      </svg>
      <span class="ai-badge" v-if="!visible">AI</span>
    </div>
    <transition name="fade">
      <div v-if="visible" class="ai-panel">
        <div class="ai-header">
          <div class="ai-header-left">
            <div class="ai-avatar">AI</div>
            <div>
              <div class="ai-title">智能客服助手</div>
              <div class="ai-subtitle">在线 · 为您解答停车相关问题</div>
            </div>
          </div>
        </div>
        <div class="ai-body" ref="bodyRef">
          <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', m.role]">
            <div class="bubble">{{ m.content }}</div>
          </div>
          <div v-if="loading" class="ai-msg bot">
            <div class="bubble typing"><span></span><span></span><span></span></div>
          </div>
        </div>
        <div class="ai-quick">
          <span class="quick-btn" @click="quick('如何缴费')">如何缴费</span>
          <span class="quick-btn" @click="quick('如何邀请访客')">邀请访客</span>
          <span class="quick-btn" @click="quick('月卡怎么续费')">月卡续费</span>
        </div>
        <div class="ai-input">
          <input v-model="input" placeholder="输入您的问题..." @keyup.enter="send" />
          <button @click="send" :disabled="loading">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import request from '@/utils/request'

const visible = ref(false)
const input = ref('')
const loading = ref(false)
const bodyRef = ref(null)
const messages = ref([
  { role: 'bot', content: '您好！我是智慧停车AI助手，您可以问我：如何缴费、如何邀请访客、如何预约车位等问题。' }
])

const quick = (q) => { input.value = q; send() }

const send = async () => {
  const q = input.value.trim()
  if (!q || loading.value) return
  messages.value.push({ role: 'user', content: q })
  input.value = ''
  loading.value = true
  await nextTick(); scrollBottom()
  try {
    const res = await request({ url: '/ai/chat', method: 'post', data: { question: q } })
    messages.value.push({ role: 'bot', content: res.data.answer })
  } catch (e) {
    messages.value.push({ role: 'bot', content: '抱歉，服务暂时不可用。' })
  } finally {
    loading.value = false
    await nextTick(); scrollBottom()
  }
}

const scrollBottom = () => {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}
</script>

<style scoped>
.ai-fab {
  position: fixed; right: 28px; bottom: 28px; z-index: 9999;
  width: 60px; height: 60px; border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff; display: flex; align-items: center; justify-content: center;
  cursor: pointer; box-shadow: 0 6px 20px rgba(102,126,234,.45);
  transition: all .3s ease; position: relative;
}
.ai-fab:hover { transform: scale(1.08); box-shadow: 0 8px 25px rgba(102,126,234,.6); }
.ai-badge {
  position: absolute; top: -2px; right: -2px;
  background: #ff4757; color: #fff; font-size: 10px; font-weight: bold;
  padding: 2px 6px; border-radius: 10px;
}
.ai-panel {
  position: fixed; right: 28px; bottom: 100px; z-index: 9999;
  width: 380px; height: 540px; background: #fff; border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0,0,0,.18); display: flex; flex-direction: column; overflow: hidden;
}
.fade-enter-active, .fade-leave-active { transition: all .25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(10px) scale(.96); }
.ai-header {
  padding: 14px 16px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff;
}
.ai-header-left { display: flex; align-items: center; gap: 10px; }
.ai-avatar {
  width: 38px; height: 38px; border-radius: 50%; background: rgba(255,255,255,.25);
  display: flex; align-items: center; justify-content: center; font-weight: bold;
}
.ai-title { font-weight: 600; font-size: 15px; }
.ai-subtitle { font-size: 11px; opacity: .85; }
.ai-body { flex: 1; padding: 14px; overflow-y: auto; background: #f5f7fa; }
.ai-msg { margin-bottom: 12px; display: flex; }
.ai-msg.user { justify-content: flex-end; }
.bubble {
  max-width: 82%; padding: 10px 14px; border-radius: 14px; font-size: 13px; line-height: 1.6;
}
.ai-msg.user .bubble { background: #667eea; color: #fff; border-bottom-right-radius: 4px; }
.ai-msg.bot .bubble { background: #fff; color: #333; border-bottom-left-radius: 4px; box-shadow: 0 1px 2px rgba(0,0,0,.05); }
.typing { display: flex; gap: 4px; align-items: center; padding: 12px 16px; }
.typing span {
  width: 7px; height: 7px; background: #bbb; border-radius: 50%;
  animation: blink 1.2s infinite;
}
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100%{opacity:.3} 40%{opacity:1} }
.ai-quick { display: flex; gap: 6px; padding: 8px 14px; background: #f5f7fa; border-top: 1px solid #eee; }
.quick-btn {
  font-size: 12px; padding: 4px 10px; background: #fff; border: 1px solid #dcdfe6;
  border-radius: 12px; cursor: pointer; color: #606266;
}
.quick-btn:hover { border-color: #667eea; color: #667eea; }
.ai-input { display: flex; padding: 10px; border-top: 1px solid #eee; gap: 8px; }
.ai-input input { flex: 1; padding: 10px 14px; border: 1px solid #e4e7ed; border-radius: 20px; outline: none; font-size: 13px; }
.ai-input input:focus { border-color: #667eea; }
.ai-input button {
  width: 38px; height: 38px; border-radius: 50%; border: none;
  background: linear-gradient(135deg, #667eea, #764ba2); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.ai-input button:disabled { opacity: .5; }
</style>
