<template>
  <div class="ai">
    <transition name="ai-pop">
      <section v-if="visible" class="ai__panel">
        <header class="ai__head">
          <div class="ai__avatar">星</div>
          <div class="ai__id">
            <b>星仔 · 智能客服</b>
            <i><span class="ai__live" /> 在线解答停车问题</i>
          </div>
          <button class="ai__close" @click="visible = false" title="收起">
            <el-icon><Close /></el-icon>
          </button>
        </header>

        <div ref="bodyRef" class="ai__body">
          <div v-for="(m, i) in messages" :key="i" class="ai__msg" :class="m.role">
            <span v-if="m.role === 'bot'" class="ai__mini">星</span>
            <div class="ai__bubble">{{ m.content }}</div>
          </div>
          <div v-if="loading" class="ai__msg bot">
            <span class="ai__mini">星</span>
            <div class="ai__bubble ai__typing"><i /><i /><i /></div>
          </div>
        </div>

        <div class="ai__quick">
          <button v-for="q in quicks" :key="q" class="ai__chip" @click="ask(q)">{{ q }}</button>
        </div>

        <footer class="ai__input">
          <input
            v-model="input"
            placeholder="描述你的问题，例如：月卡怎么续费"
            maxlength="120"
            @keyup.enter="send"
          />
          <button :disabled="loading || !input.trim()" @click="send">
            <el-icon><Promotion /></el-icon>
          </button>
        </footer>
      </section>
    </transition>

    <button class="ai__fab" :class="{ 'is-open': visible }" @click="visible = !visible" :title="visible ? '收起助手' : 'AI 智能客服'">
      <el-icon><ChatDotRound v-if="!visible" /><Close v-else /></el-icon>
      <span v-if="!visible" class="ai__fab-tip">AI</span>
    </button>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import request from '@/utils/request'

const visible = ref(false)
const input = ref('')
const loading = ref(false)
const bodyRef = ref(null)

const quicks = ['如何缴费', '邀请访客', '月卡续费', '车位预约']

const messages = ref([
  {
    role: 'bot',
    content: '你好，我是星仔。可以问我：如何缴费、如何邀请访客、车位怎么预约、月卡如何续费。'
  }
])

const scrollBottom = () => {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}

const ask = (q) => {
  input.value = q
  send()
}

const send = async () => {
  const q = input.value.trim()
  if (!q || loading.value) return
  messages.value.push({ role: 'user', content: q })
  input.value = ''
  loading.value = true
  await nextTick()
  scrollBottom()
  try {
    // 后端 POST /api/ai/chat 接收 { question }，返回 { answer, provider, model }
    const res = await request({ url: '/ai/chat', method: 'post', data: { question: q } })
    const data = res.data || {}
    messages.value.push({ role: 'bot', content: data.answer || '暂时没有找到答案，请换个说法试试。' })
  } catch (e) {
    messages.value.push({ role: 'bot', content: '智能客服暂时不可用，请稍后再试或联系物业前台。' })
  } finally {
    loading.value = false
    await nextTick()
    scrollBottom()
  }
}
</script>

<style scoped>
.ai__fab {
  position: fixed;
  right: 26px;
  bottom: 26px;
  z-index: 2000;
  width: 54px;
  height: 54px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: #fff;
  font-size: 22px;
  display: grid;
  place-items: center;
  background: var(--sp-grad-brand);
  box-shadow: 0 10px 26px var(--sp-brand-soft), 0 2px 6px rgba(15, 23, 42, 0.16);
  transition: transform var(--sp-dur-comp) var(--sp-ease), box-shadow var(--sp-dur-comp) var(--sp-ease);
}

.ai__fab:hover {
  transform: translateY(-3px) scale(1.04);
}

.ai__fab.is-open {
  background: var(--sp-surface);
  color: var(--sp-text-2);
  border: 1px solid var(--sp-card-border);
}

.ai__fab-tip {
  position: absolute;
  top: -3px;
  right: -3px;
  padding: 1px 6px;
  border-radius: 9px;
  background: var(--sp-gold);
  color: #fff;
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.ai__panel {
  position: fixed;
  right: 26px;
  bottom: 92px;
  z-index: 2000;
  width: 372px;
  max-width: calc(100vw - 36px);
  height: 534px;
  max-height: calc(100vh - 130px);
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}

.ai__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: var(--sp-grad-brand);
  color: #fff;
}

.ai__avatar,
.ai__mini {
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-weight: 800;
  flex: none;
}

.ai__avatar {
  width: 36px;
  height: 36px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 14px;
}

.ai__id {
  flex: 1;
  min-width: 0;
}

.ai__id b {
  display: block;
  font-size: 14px;
  letter-spacing: 0.02em;
}

.ai__id i {
  display: flex;
  align-items: center;
  gap: 5px;
  font-style: normal;
  font-size: 11px;
  opacity: 0.88;
  margin-top: 2px;
}

.ai__live {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #B6F0D8;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.25);
}

.ai__close {
  border: none;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.ai__body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: var(--sp-surface-2);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai__msg {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.ai__msg.user {
  justify-content: flex-end;
}

.ai__mini {
  width: 26px;
  height: 26px;
  font-size: 11px;
  color: #fff;
  background: var(--sp-grad-brand);
  margin-top: 2px;
}

.ai__bubble {
  max-width: 78%;
  padding: 10px 13px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai__msg.bot .ai__bubble {
  background: var(--sp-surface);
  border: 1px solid var(--sp-card-border);
  color: var(--sp-text);
  border-top-left-radius: 4px;
}

.ai__msg.user .ai__bubble {
  background: var(--sp-brand);
  color: #fff;
  border-top-right-radius: 4px;
}

.ai__typing {
  display: flex;
  gap: 4px;
  align-items: center;
}

.ai__typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sp-text-3);
  animation: ai-blink 1.2s infinite;
}

.ai__typing i:nth-child(2) { animation-delay: 0.18s; }
.ai__typing i:nth-child(3) { animation-delay: 0.36s; }

@keyframes ai-blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}

.ai__quick {
  display: flex;
  gap: 6px;
  padding: 8px 14px;
  background: var(--sp-surface-2);
  border-top: 1px solid var(--sp-divider);
  flex-wrap: wrap;
}

.ai__chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  color: var(--sp-text-2);
  font-size: 11.5px;
  cursor: pointer;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.ai__chip:hover {
  color: var(--sp-brand);
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
}

.ai__input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--sp-divider);
  background: var(--sp-surface);
}

.ai__input input {
  flex: 1;
  min-width: 0;
  padding: 9px 13px;
  border-radius: 999px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface-2);
  color: var(--sp-text);
  font-size: 13px;
  outline: none;
}

.ai__input input:focus {
  border-color: var(--sp-brand);
  box-shadow: 0 0 0 3px var(--sp-brand-soft);
}

.ai__input button {
  width: 36px;
  height: 36px;
  flex: none;
  border: none;
  border-radius: 50%;
  background: var(--sp-grad-brand);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 16px;
}

.ai__input button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-pop-enter-active,
.ai-pop-leave-active {
  transition: all var(--sp-dur-comp) var(--sp-ease);
}

.ai-pop-enter-from,
.ai-pop-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.97);
}
</style>
