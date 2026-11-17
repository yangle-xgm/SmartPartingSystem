/**
 * 车牌展示兜底：API 层应返回明文车牌，但服务实例未升级/解密异常时会拿到 AES 密文，
 * 直接渲染就是乱码。这里统一回退到脱敏车牌，绝不把密文当车牌展示。
 */

export function isEncryptedPlate(value) {
  const s = String(value || '')
  if (!s || /[\u4e00-\u9fa5]/.test(s)) return false
  return s.length >= 16 && /^[A-Za-z0-9+/=_-]+$/.test(s)
}

export function displayPlate(row) {
  if (!row) return '—'
  if (!isEncryptedPlate(row.plateNo)) {
    return row.plateNo || row.plateNoMasked || '—'
  }
  return row.plateNoMasked || '—'
}
