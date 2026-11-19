import { getPayChannels, alipayCashier, alipayQuery } from '@/api/order'

/**
 * 支付宝沙箱支付辅助（手机网站支付）。
 * 与 web 端同一套思路：收银台表单跳转 + 主动查询轮询确认到账。
 */
const POLL_INTERVAL_MS = 1500
const POLL_TIMES = 40

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 当前页面的完整地址，作为付款后的回跳地址。
 * 必需：后端配置的 return-url 是 http://localhost:3001，手机上 localhost 指向手机自己，
 * 付款成功后支付宝一跳就「页面打不开 / 异常退出」。
 */
function currentReturnUrl() {
  const { origin, pathname, search } = window.location
  return origin + pathname + (search || '')
}

export async function isAlipaySandboxReady() {
  try {
    const res = await getPayChannels()
    return Boolean(res?.data?.alipaySandboxReady)
  } catch (e) {
    return false
  }
}

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

export async function payWithAlipaySandbox(payNo) {
  let res
  try {
    res = await alipayCashier(payNo, 'h5', currentReturnUrl())
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
      // 继续轮询
    }
  }
  return { ok: false, message: '等待支付结果超时；若已完成付款，请稍后在支付记录中确认' }
}
