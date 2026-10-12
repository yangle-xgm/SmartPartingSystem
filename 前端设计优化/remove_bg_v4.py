# -*- coding: utf-8 -*-
"""V4 专用抠图：低饱和(灰系) + 亮度连续 flood-fill，适配浅灰~深灰渐变背景。"""
from PIL import Image
import numpy as np
from collections import deque

SRC = r"D:\JAVA EE\SmartPartingSystem\前端设计优化\starzo_raw_v6.png"
DST = r"D:\JAVA EE\SmartPartingSystem\smart-parking-web\src\assets\images\starzo.png"

img = Image.open(SRC).convert("RGB")
a = np.array(img).astype(np.int16)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
h, w = a.shape[:2]

gray = a.mean(axis=2)
std = a.std(axis=2)

STD_MAX = 22        # 低饱和灰系判定
LUM_STEP = 28       # 相邻亮度最大落差（渐变背景畅通，主体阻断）

cand = std < STD_MAX
visited = np.zeros((h, w), bool)
q = deque()
for x in range(w):
    for y in (0, h - 1):
        if cand[y, x] and not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))
for y in range(h):
    for x in (0, w - 1):
        if cand[y, x] and not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))
while q:
    y, x = q.popleft()
    gv = gray[y, x]
    for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
        if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx] and cand[ny, nx] and abs(gray[ny, nx] - gv) < LUM_STEP:
            visited[ny, nx] = True
            q.append((ny, nx))

alpha = np.full((h, w), 255, np.uint8)
alpha[visited] = 0

# 边界过渡：未访问但低饱和的边界像素按 std 渐变
vis_u8 = visited.astype(np.uint8)
dil = np.zeros((h, w), np.uint8)
dil |= np.roll(vis_u8, 1, 0) | np.roll(vis_u8, -1, 0) | np.roll(vis_u8, 1, 1) | np.roll(vis_u8, -1, 1)
edge = dil.astype(bool) & ~visited & (std < 34)
alpha[edge] = np.clip((std[edge] - 13) * 22, 0, 255).astype(np.uint8)

out = np.dstack([r, g, b, alpha]).astype(np.uint8)
res = Image.fromarray(out, "RGBA")
bbox = res.getbbox()
if bbox:
    res = res.crop(bbox)
res = res.resize((768, 768), Image.LANCZOS)
res.save(DST, "PNG")
print("saved:", DST, res.size)
print("transparent ratio:", round(float((alpha == 0).mean()), 3))
