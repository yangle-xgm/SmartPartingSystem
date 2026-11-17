import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '@/api/notify'
import { getUserId } from '@/api/user'

/**
 * 未读通知角标（D8）
 *
 * 历史实现把未读数放在 layout 的局部 ref 里，只在「路由变化」和「60s 定时器」时刷新，
 * 通知中心里点「标记已读 / 全部已读」后角标最多要等一分钟才变，看起来像没生效。
 * 改成全局 store 后，任何页面完成已读操作都可以直接 refresh 角标。
 */
export const useNotifyStore = defineStore('notify', () => {
  const unread = ref(0)
  const loaded = ref(false)

  async function refresh() {
    const userId = getUserId()
    if (!userId) {
      unread.value = 0
      return 0
    }
    try {
      const res = await getUnreadCount(userId)
      unread.value = Number(res.data) || 0
      loaded.value = true
    } catch (e) {
      // 静默：角标失败不影响主流程
    }
    return unread.value
  }

  /** 本地先减，给即时反馈；随后以 refresh() 的服务端结果为准 */
  function decrement(n = 1) {
    unread.value = Math.max(0, unread.value - n)
  }

  function reset() {
    unread.value = 0
    loaded.value = false
  }

  return { unread, loaded, refresh, decrement, reset }
})
