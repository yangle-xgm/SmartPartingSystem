# 智慧星河 V5.0 · Vue3 落地指南（smart-parking-web）

> 技术栈：Vue 3.4 + Vite + Element Plus + Pinia + Vue Router（与计划书 2.3/2.5 一致）
> 本目录文件复制进你的 `smart-parking-web` 项目即可使用。

## 一、文件对应关系

| 本目录文件 | 复制到项目位置 | 作用 |
|-----------|---------------|------|
| `src/styles/tokens.css` | `smart-parking-web/src/styles/tokens.css` | 全站设计变量（颜色/圆角/间距/动效/图表色） |
| `src/views/login/LoginView.vue` | `smart-parking-web/src/views/login/index.vue` | 探照灯登录页（建议改名 index.vue，路由更简洁） |

## 二、接入步骤（三步）

### 第 1 步：全局引入 tokens

`src/main.js` 中加上：

```js
import './styles/tokens.css'
```

### 第 2 步：注册路由

`src/router/index.js` 中加上：

```js
{
  path: '/login',
  name: 'Login',
  component: () => import('@/views/login/index.vue')
}
```

### 第 3 步：改登录跳转

`LoginView.vue` 中 `handleEnter` / `handleMore` 改成实际跳转：

```js
import { useRouter } from 'vue-router'
const router = useRouter()
const handleEnter = () => router.push('/dashboard')
const handleMore = () => router.push('/intro')
```

## 三、可选：Element Plus 深色模式

如果管理端要用 EP 组件（表格、弹窗等），让它们自动继承本设计系统：

```js
// main.js
import 'element-plus/theme-chalk/dark/css-vars.css'
document.documentElement.classList.add('dark')
```

EP 的主色/成功/危险/边框/圆角会被 tokens.css 里的 `html.dark { ... }` 覆盖为智慧星河配色。

## 四、后续页面怎么生成（保持全站一致的关键）

1. 每个新页面先粘贴「智慧星河前端提示词V5.0_光感版.md」第四章的**页面生成模板**给 AI 工具；
2. 生成的代码里，颜色/圆角/间距/动效**全部引用 `var(--sp-*)`**，禁止写死色值字号；
3. 卡片用 `.sp-card`，页面容器用 `.sp-page`，数字对齐加 `.sp-num`；
4. 图表色板用 `--sp-chart-1 ~ 5`，状态色用 `--sp-teal / --sp-gold / --sp-danger`；
5. 每个页面必做四态：loading（星仔旋转）/ empty（星仔场景）/ error（温和提示+重试）/ success（星光反馈）。

## 五、常见问题

| 问题 | 解决 |
|------|------|
| 自定义光标不出现 | 正常：触屏设备自动隐藏；桌面端才显示金色光点+圆环 |
| 移动端没有探照灯 | 正常：自动切换为"光束自动漫游 + 触摸跟随"，不会空白 |
| 光标悬停按钮圆环不放大 | 检查按钮是否加了 `@mouseenter/@mouseleave` 绑定 `ringActive` |
| 页面切换后动画还在跑 | 已在 `onUnmounted` 清理 rAF/监听/定时器，无需手动处理 |
| 登录页盖住全局路由布局 | 本页用 `position: fixed` 全屏，建议作为独立路由、不套用管理端 Layout |

## 六、性能自查

- Chrome DevTools → Performance 录制 10s，平均帧率 ≥ 55fps；
- 粒子总数 168 颗（130 远景星 + 38 光尘），勿擅自增加；
- 若低端机掉帧：把 `stars` 数量改成 `100 + 30`，或把光斑半径 540 改为 460。
