import { getPayChannels, alipayCashier, alipayQuery } from '@/api/payment'

/**
 * 支付宝沙箱支付辅助（电脑网站支付）。
 *
 * 链路：收银台下单拿表单 → 新窗口自动提交到沙箱收银台 → 轮询主动查询确认到账。
 *
 * 为什么用"表单跳转"而不是二维码：前端没有二维码渲染库，而电脑网站支付
 * 返回的正是自动提交表单，零依赖、且与官方 PC 接入方式一致。
 *
 * 为什么用"轮询查询"而不是等异步通知：异步通知要求 notify_url 公网可达，
 * 本机开发/演示拿不到公网地址。主动查询是生产里标准的掉单兜底手段，这里复用。
 */
const POLL_INTERVAL_MS = 1500
const POLL_TIMES = 40 // 最多等 60 秒

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 当前页完整地址，作为付款后的回跳地址（配置里的 return-url 是固定的本机地址） */
function currentReturnUrl() {
  const { origin, pathname, search } = window.location
  return origin + pathname + (search || '')
}

/** 后端是否已切到支付宝沙箱（配置完整可用） */
export async function isAlipaySandboxReady() {
  try {
    const res = await getPayChannels()
    return Boolean(res?.data?.alipaySandboxReady)
  } catch (e) {
    return false
  }
}

/** 把收银台表单写入新窗口并自动提交 */
function submitFormInNewWindow(form) {
  const win = window.open('', '_blank')
  if (!win) {
    return false
  }
  win.document.open()
  win.document.write(form)
  win.document.close()
  return true
}

/**
 * 走支付宝沙箱完成支付。
 * @param {string} payNo 支付流水号
 * @returns {Promise<{ok: boolean, message?: string, tradeNo?: string}>}
 */
export async function payWithAlipaySandbox(payNo) {
  let res
  try {
    res = await alipayCashier(payNo, 'pc', currentReturnUrl())
  } catch (e) {
    return { ok: false, message: '支付宝收银台下单失败，请确认沙箱配置' }
  }
  const form = res?.data?.form
  if (!form) {
    return { ok: false, message: '支付宝收银台下单失败：未返回支付表单' }
  }
  if (!submitFormInNewWindow(form)) {
    return { ok: false, message: '浏览器拦截了支付窗口，请允许本站弹出窗口后重试' }
  }

  for (let i = 0; i < POLL_TIMES; i++) {
    await sleep(POLL_INTERVAL_MS)
    try {
      const q = await alipayQuery(payNo)
      if (q?.data?.settled) {
        return { ok: true, tradeNo: q.data.tradeNo }
      }
    } catch (e) {
      // 查询期间网络抖动不终止轮询，继续等
    }
  }
  return { ok: false, message: '等待支付结果超时；若已完成付款，请稍后在支付记录中确认' }
}
