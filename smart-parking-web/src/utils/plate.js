/**
 * 车牌展示兜底。
 *
 * 后端 plateNo 字段是 AES 密文入库、API 层解密后返回。一旦某个服务实例没升级
 * （或解密异常），前端拿到的就是 Base64 密文，直接渲染会变成 "3p/£VsWktbd9j8…"。
 * 这里做一层防御：密文一律回退到脱敏车牌，绝不把密文当作车牌展示。
 */

/** 密文特征：纯 ASCII 的 Base64 串（真实车牌必含汉字），且长度明显超过车牌 */
export function isEncryptedPlate(value) {
  const s = String(value || '')
  if (!s || /[\u4e00-\u9fa5]/.test(s)) return false
  return s.length >= 16 && /^[A-Za-z0-9+/=_-]+$/.test(s)
}

/** 统一的车牌展示文案：正常车牌 → 原样；密文 → 脱敏车牌；都没有 → '—' */
export function displayPlate(row) {
  if (!row) return '—'
  if (!isEncryptedPlate(row.plateNo)) {
    return row.plateNo || row.plateNoMasked || '—'
  }
  return row.plateNoMasked || '—'
}
