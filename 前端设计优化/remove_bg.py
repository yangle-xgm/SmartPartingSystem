# -*- coding: utf-8 -*-
"""去白底：flood-fill 边缘连通白色 -> 透明，边缘抗锯齿过渡。"""
from PIL import Image
import numpy as np
from collections import deque

SRC = r"D:\JAVA EE\SmartPartingSystem\前端设计优化\starzo_raw_v8.png"
DST = r"D:\JAVA EE\SmartPartingSystem\smart-parking-web\src\assets\images\starzo.png"

img = Image.open(SRC).convert("RGBA")
a = np.array(img).astype(np.int16)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
h, w = a.shape[:2]

# 偏白判定：背景浅灰渐变 min~220-240，主体香槟金 min~187，取 210 为界
white = (r > 240) & (g > 240) & (b > 240)

# BFS 标记与边缘连通的白像素（背景）
visited = np.zeros((h, w), bool)
q = deque()
for x in range(w):
    for y in (0, h - 1):
        if white[y, x] and not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))
for y in range(h):
    for x in (0, w - 1):
        if white[y, x] and not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))
while q:
    y, x = q.popleft()
    if y > 0 and white[y - 1, x] and not visited[y - 1, x]:
        visited[y - 1, x] = True; q.append((y - 1, x))
    if y < h - 1 and white[y + 1, x] and not visited[y + 1, x]:
        visited[y + 1, x] = True; q.append((y + 1, x))
    if x > 0 and white[y, x - 1] and not visited[y, x - 1]:
        visited[y, x - 1] = True; q.append((y, x - 1))
    if x < w - 1 and white[y, x + 1] and not visited[y, x + 1]:
        visited[y, x + 1] = True; q.append((y, x + 1))

alpha = a[..., 3].copy()
alpha[visited] = 0

# 背景边界抗锯齿：紧邻背景的偏白像素按白度渐变
wh = np.minimum(np.minimum(r, g), b)
vis_u8 = visited.astype(np.uint8)
dil = np.zeros((h, w), np.uint8)
dil |= np.roll(vis_u8, 1, 0) | np.roll(vis_u8, -1, 0) | np.roll(vis_u8, 1, 1) | np.roll(vis_u8, -1, 1)
edge = (dil.astype(bool)) & white & ~visited
alpha[edge] = np.clip((255 - wh[edge]) * 4.0, 0, 255).astype(np.uint8)

out = np.dstack([r, g, b, alpha]).astype(np.uint8)
res = Image.fromarray(out, "RGBA")

# 裁剪透明边界，保持宽高比贴入 768x768 透明画布（禁止拉伸变形）
bbox = res.getbbox()
if bbox:
    res = res.crop(bbox)
w, h = res.size
scale = 768 / max(w, h)
nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
res = res.resize((nw, nh), Image.LANCZOS)
canvas = Image.new('RGBA', (768, 768), (0, 0, 0, 0))
canvas.paste(res, ((768 - nw) // 2, (768 - nh) // 2))
res = canvas

import os
os.makedirs(os.path.dirname(DST), exist_ok=True)
res.save(DST, "PNG")
print("saved:", DST, res.size)
