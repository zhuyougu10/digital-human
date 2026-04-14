# Metaverse Clinic Single-Stage Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `medical-mp` 改造成“单主舞台 + 系统抽屉”的沉浸式数字人诊室，主链路统一在一个 Stage 页面内完成，并支持打字/说/点气泡三入口并行。

**Architecture:** 新增 `pages/stage/index.vue` 作为唯一主舞台，使用 `stage-store` 驱动场景状态机；保留现有 SSE/TTS/Live2D 能力，仅重构 UI 壳层与交互编排。医生推荐与预约确认通过舞台浮层承载，档案/日程/设置放入系统抽屉或次级页。

**Tech Stack:** uni-app (Vue 3), 微信小程序, Pinia, existing SSE/TTS/Live2D integration

---

## File Map

### Create
- `medical-mp/src/pages/stage/index.vue` — 单主舞台页面，承载全部主链路场景与浮层
- `medical-mp/src/components/stage/StageBackground.vue` — 动态背景层
- `medical-mp/src/components/stage/WarizaActor.vue` — 数字人容器与场景位置切换
- `medical-mp/src/components/stage/CinematicSubtitle.vue` — 电影字幕条（打字机）
- `medical-mp/src/components/stage/InputDock.vue` — 打字/语音/气泡三入口底座
- `medical-mp/src/components/stage/ChoiceBubbles.vue` — 候选答案玻璃气泡
- `medical-mp/src/components/stage/DoctorFlowPanel.vue` — 医生 Cover Flow 浮层
- `medical-mp/src/components/stage/SystemDrawer.vue` — 档案/日程/设置抽屉
- `medical-mp/src/stores/stage.js` — 单一状态机与输入分发
- `medical-mp/scripts/verify-stage-config.mjs` — 配置与资源验证脚本（TDD 验证）
- `medical-mp/scripts/verify-stage-state-machine.mjs` — 状态机行为验证脚本（TDD 验证）

### Modify
- `medical-mp/src/pages.json` — 将首页路由切换到 `pages/stage/index`，弱化 tabBar
- `medical-mp/src/App.vue` — 注入暗色玻璃主题 token 和基础全局样式
- `medical-mp/src/pages/chat/chat.vue` — 保留为兼容入口，重定向到 `stage`（避免旧入口断链）
- `medical-mp/src/components/DoctorCard.vue` — 适配浮层卡片视觉（玻璃材质）
- `medical-mp/src/components/AppointmentCard.vue`（若存在）— 适配玻璃材质

### Test / Verify
- `medical-mp/scripts/verify-stage-config.mjs`
- `medical-mp/scripts/verify-stage-state-machine.mjs`
- Build command: `npm run build:mp-weixin`

---

### Task 1: 路由与主题地基（先让 Stage 成为主入口）

**Files:**
- Modify: `medical-mp/src/pages.json`
- Modify: `medical-mp/src/App.vue`
- Create: `medical-mp/scripts/verify-stage-config.mjs`
- Test: `medical-mp/scripts/verify-stage-config.mjs`

- [ ] **Step 1: Write the failing test (config verifier)**

```js
// medical-mp/scripts/verify-stage-config.mjs
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(process.cwd(), 'src')
const pages = JSON.parse(fs.readFileSync(path.join(root, 'pages.json'), 'utf8'))

const hasStagePage = pages.pages?.some((p) => p.path === 'pages/stage/index')
const firstPageIsStage = pages.pages?.[0]?.path === 'pages/stage/index'

if (!hasStagePage) {
  throw new Error('pages.json missing pages/stage/index')
}
if (!firstPageIsStage) {
  throw new Error('pages[0] must be pages/stage/index for single-stage entry')
}

console.log('verify-stage-config: PASS')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd medical-mp && node scripts/verify-stage-config.mjs`
Expected: FAIL with `missing pages/stage/index` or `pages[0] must be pages/stage/index`

- [ ] **Step 3: Write minimal implementation for route/theme baseline**

```json
// medical-mp/src/pages.json (snippet)
{
  "pages": [
    {
      "path": "pages/stage/index",
      "style": {
        "navigationStyle": "custom"
      }
    }
  ]
}
```

```vue
<!-- medical-mp/src/App.vue (style snippet) -->
<style>
page {
  --stage-bg-0: #0b1220;
  --stage-bg-1: #111b2e;
  --stage-primary: #56c6e6;
  --stage-accent: #7cf0d6;
  --stage-text-main: #eaf4ff;
  --stage-text-sub: #a9b8cc;
  --glass-bg: rgba(255, 255, 255, 0.1);
  --glass-border: rgba(255, 255, 255, 0.24);
  background: linear-gradient(180deg, var(--stage-bg-0), var(--stage-bg-1));
  color: var(--stage-text-main);
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd medical-mp && node scripts/verify-stage-config.mjs`
Expected: PASS with `verify-stage-config: PASS`

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/pages.json medical-mp/src/App.vue medical-mp/scripts/verify-stage-config.mjs
git commit -m "feat: set stage as single entry with immersive theme tokens"
```

---

### Task 2: 建立 Stage 状态机（单一真相源）

**Files:**
- Create: `medical-mp/src/stores/stage.js`
- Create: `medical-mp/scripts/verify-stage-state-machine.mjs`
- Test: `medical-mp/scripts/verify-stage-state-machine.mjs`

- [ ] **Step 1: Write the failing test (state machine verifier)**

```js
// medical-mp/scripts/verify-stage-state-machine.mjs
import { useStageMachineForTest } from '../src/stores/stage.js'

const m = useStageMachineForTest()

if (m.state.scene !== 'idle') {
  throw new Error('initial scene must be idle')
}

m.dispatchInput({ source: 'voice', content: '我肚子痛' })
if (m.state.scene !== 'collecting') {
  throw new Error('scene should move to collecting after first input')
}

m.onTriageReady({ department: '消化内科' })
if (m.state.scene !== 'doctor_flow') {
  throw new Error('scene should move to doctor_flow after triage')
}

console.log('verify-stage-state-machine: PASS')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
Expected: FAIL with module/function missing (`useStageMachineForTest` not found)

- [ ] **Step 3: Write minimal implementation of state machine**

```js
// medical-mp/src/stores/stage.js
const initialState = () => ({
  scene: 'idle',
  subtitle: '我是你的医疗助手安禾',
  lastInput: null,
  drawerOpen: false,
  doctorFlowOpen: false
})

export function createStageMachine() {
  const state = initialState()

  function dispatchInput(payload) {
    state.lastInput = payload
    if (state.scene === 'idle') state.scene = 'collecting'
    else if (state.scene === 'collecting') state.scene = 'questioning'
  }

  function onTriageReady(result) {
    state.triage = result
    state.scene = 'doctor_flow'
    state.doctorFlowOpen = true
  }

  return { state, dispatchInput, onTriageReady }
}

export function useStageMachineForTest() {
  return createStageMachine()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
Expected: PASS with `verify-stage-state-machine: PASS`

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/stores/stage.js medical-mp/scripts/verify-stage-state-machine.mjs
git commit -m "feat: add single-stage state machine with unified input dispatch"
```

---

### Task 3: 搭建主舞台骨架页（无业务替换，先跑通场景框架）

**Files:**
- Create: `medical-mp/src/pages/stage/index.vue`
- Create: `medical-mp/src/components/stage/StageBackground.vue`
- Create: `medical-mp/src/components/stage/WarizaActor.vue`
- Create: `medical-mp/src/components/stage/CinematicSubtitle.vue`
- Test: `medical-mp/src/pages/stage/index.vue`

- [ ] **Step 1: Write the failing build check**

Run: `cd medical-mp && npm run build:mp-weixin`
Expected: FAIL because `pages/stage/index.vue` and imported stage components do not exist

- [ ] **Step 2: Create minimal stage page skeleton**

```vue
<!-- medical-mp/src/pages/stage/index.vue -->
<template>
  <view class="stage-page">
    <StageBackground :scene="machine.state.scene" />
    <WarizaActor :scene="machine.state.scene" />
    <CinematicSubtitle :text="machine.state.subtitle" />
  </view>
</template>

<script setup>
import { createStageMachine } from '@/stores/stage'
import StageBackground from '@/components/stage/StageBackground.vue'
import WarizaActor from '@/components/stage/WarizaActor.vue'
import CinematicSubtitle from '@/components/stage/CinematicSubtitle.vue'

const machine = createStageMachine()
</script>
```

- [ ] **Step 3: Create minimal stage components**

```vue
<!-- StageBackground.vue -->
<template><view class="stage-background" :data-scene="scene" /></template>
<script setup>defineProps({ scene: String })</script>
```

```vue
<!-- WarizaActor.vue -->
<template><view class="wariza-actor">安禾</view></template>
<script setup>defineProps({ scene: String })</script>
```

```vue
<!-- CinematicSubtitle.vue -->
<template><view class="cinematic-subtitle">{{ text }}</view></template>
<script setup>defineProps({ text: String })</script>
```

- [ ] **Step 4: Run build to verify it passes**

Run: `cd medical-mp && npm run build:mp-weixin`
Expected: PASS (允许 Sass deprecation warning)

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/pages/stage/index.vue medical-mp/src/components/stage/StageBackground.vue medical-mp/src/components/stage/WarizaActor.vue medical-mp/src/components/stage/CinematicSubtitle.vue
git commit -m "feat: scaffold immersive stage page and base layers"
```

---

### Task 4: 接入三入口 Input Dock（打字/说/点气泡）

**Files:**
- Create: `medical-mp/src/components/stage/InputDock.vue`
- Create: `medical-mp/src/components/stage/ChoiceBubbles.vue`
- Modify: `medical-mp/src/pages/stage/index.vue`
- Test: `medical-mp/scripts/verify-stage-state-machine.mjs`

- [ ] **Step 1: Extend failing test for three-input parity**

```js
// append to verify-stage-state-machine.mjs
const m2 = useStageMachineForTest()
m2.dispatchInput({ source: 'typed', content: '头疼' })
if (m2.state.scene !== 'collecting') throw new Error('typed should enter collecting')

const m3 = useStageMachineForTest()
m3.dispatchInput({ source: 'bubble', content: '绞痛，很剧烈' })
if (m3.state.scene !== 'collecting') throw new Error('bubble should enter collecting')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
Expected: FAIL if `dispatchInput` does not handle `typed/bubble`

- [ ] **Step 3: Implement InputDock + ChoiceBubbles and wire unified event**

```vue
<!-- InputDock.vue -->
<template>
  <view class="input-dock glass">
    <input class="dock-input" v-model="typed" placeholder="输入症状" @confirm="emitTyped" />
    <button class="dock-voice" @click="$emit('voice')">🎙️</button>
    <button class="dock-bubble" @click="$emit('toggle-bubbles')">气泡</button>
  </view>
</template>
<script setup>
import { ref } from 'vue'
const typed = ref('')
const emit = defineEmits(['typed', 'voice', 'toggle-bubbles'])
const emitTyped = () => {
  if (!typed.value) return
  emit('typed', typed.value)
  typed.value = ''
}
</script>
```

```vue
<!-- ChoiceBubbles.vue -->
<template>
  <view class="choice-bubbles">
    <button v-for="it in options" :key="it" class="bubble" @click="$emit('pick', it)">{{ it }}</button>
  </view>
</template>
<script setup>
defineProps({ options: { type: Array, default: () => [] } })
defineEmits(['pick'])
</script>
```

```vue
<!-- index.vue integration snippet -->
<InputDock
  @typed="(text) => machine.dispatchInput({ source: 'typed', content: text })"
  @voice="machine.dispatchInput({ source: 'voice', content: '[voice]' })"
  @toggle-bubbles="showBubbles = !showBubbles"
/>
<ChoiceBubbles
  v-if="showBubbles"
  :options="['绞痛，很剧烈', '吃了海鲜', '伴随呕吐']"
  @pick="(text) => machine.dispatchInput({ source: 'bubble', content: text })"
/>
```

- [ ] **Step 4: Run state verifier**

Run: `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
Expected: PASS with three-input parity checks

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/components/stage/InputDock.vue medical-mp/src/components/stage/ChoiceBubbles.vue medical-mp/src/pages/stage/index.vue medical-mp/scripts/verify-stage-state-machine.mjs
git commit -m "feat: add unified typed voice bubble input dock"
```

---

### Task 5: 接入医生推荐浮层与预约确认动作（舞台内闭环）

**Files:**
- Create: `medical-mp/src/components/stage/DoctorFlowPanel.vue`
- Modify: `medical-mp/src/pages/stage/index.vue`
- Modify: `medical-mp/src/stores/stage.js`
- Test: `medical-mp/scripts/verify-stage-state-machine.mjs`

- [ ] **Step 1: Add failing assertions for doctor flow and booking scene**

```js
// append to verify-stage-state-machine.mjs
const m4 = useStageMachineForTest()
m4.onTriageReady({ department: '消化内科' })
if (!m4.state.doctorFlowOpen) throw new Error('doctor flow panel should open')

m4.confirmBooking({ doctorId: 1 })
if (m4.state.scene !== 'booking_confirm') throw new Error('scene should become booking_confirm')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
Expected: FAIL with `confirmBooking is not a function` or scene mismatch

- [ ] **Step 3: Implement minimal doctor flow + booking confirm transition**

```js
// stage.js (addition)
function confirmBooking(payload) {
  state.booking = payload
  state.scene = 'booking_confirm'
  state.doctorFlowOpen = false
}

return { state, dispatchInput, onTriageReady, confirmBooking }
```

```vue
<!-- DoctorFlowPanel.vue -->
<template>
  <view class="doctor-flow glass" v-if="open">
    <view class="doctor-card" v-for="d in doctors" :key="d.id">
      <view>{{ d.name }}（{{ d.department }}）</view>
      <button @click="$emit('book', d)">立即预约</button>
    </view>
  </view>
</template>
<script setup>
defineProps({ open: Boolean, doctors: { type: Array, default: () => [] } })
defineEmits(['book'])
</script>
```

```vue
<!-- index.vue integration -->
<DoctorFlowPanel
  :open="machine.state.doctorFlowOpen"
  :doctors="doctorCandidates"
  @book="(d) => machine.confirmBooking({ doctorId: d.id })"
/>
```

- [ ] **Step 4: Run state verifier and build**

Run:
- `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
- `cd medical-mp && npm run build:mp-weixin`

Expected:
- state verifier PASS
- build PASS

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/components/stage/DoctorFlowPanel.vue medical-mp/src/pages/stage/index.vue medical-mp/src/stores/stage.js medical-mp/scripts/verify-stage-state-machine.mjs
git commit -m "feat: add in-stage doctor flow and booking confirm transition"
```

---

### Task 6: 系统抽屉与旧入口兼容（不破坏现有外链/历史路由）

**Files:**
- Create: `medical-mp/src/components/stage/SystemDrawer.vue`
- Modify: `medical-mp/src/pages/stage/index.vue`
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Test: `medical-mp/scripts/verify-stage-config.mjs`

- [ ] **Step 1: Add failing config assertion for legacy entry compatibility**

```js
// append to verify-stage-config.mjs
const chatFile = fs.readFileSync(path.join(root, 'pages/chat/chat.vue'), 'utf8')
if (!chatFile.includes('redirectTo') && !chatFile.includes('reLaunch')) {
  throw new Error('legacy chat entry should redirect to stage page')
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd medical-mp && node scripts/verify-stage-config.mjs`
Expected: FAIL with `legacy chat entry should redirect to stage page`

- [ ] **Step 3: Implement SystemDrawer and chat compatibility redirect**

```vue
<!-- SystemDrawer.vue -->
<template>
  <view class="system-drawer glass" v-if="open">
    <button @click="$emit('nav', 'profile')">医疗档案</button>
    <button @click="$emit('nav', 'schedule')">预约日程</button>
    <button @click="$emit('nav', 'settings')">设置</button>
    <button @click="$emit('close')">关闭</button>
  </view>
</template>
<script setup>
defineProps({ open: Boolean })
defineEmits(['close', 'nav'])
</script>
```

```vue
<!-- pages/chat/chat.vue (minimal redirect) -->
<script setup>
onLoad(() => {
  uni.reLaunch({ url: '/pages/stage/index' })
})
</script>
```

- [ ] **Step 4: Run config verifier + build**

Run:
- `cd medical-mp && node scripts/verify-stage-config.mjs`
- `cd medical-mp && npm run build:mp-weixin`

Expected:
- config verifier PASS
- build PASS

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/components/stage/SystemDrawer.vue medical-mp/src/pages/stage/index.vue medical-mp/src/pages/chat/chat.vue medical-mp/scripts/verify-stage-config.mjs
git commit -m "feat: add system drawer and legacy chat-to-stage redirect"
```

---

### Task 7: 视觉收口与验收清单

**Files:**
- Modify: `medical-mp/src/components/DoctorCard.vue`
- Modify: `medical-mp/src/components/AppointmentCard.vue`（如存在）
- Modify: `medical-mp/src/pages/stage/index.vue`
- Test: `medical-mp/scripts/verify-stage-config.mjs`, `medical-mp/scripts/verify-stage-state-machine.mjs`

- [ ] **Step 1: Write failing visual contract check (string-level guard)**

```js
// append to verify-stage-config.mjs
const stageFile = fs.readFileSync(path.join(root, 'pages/stage/index.vue'), 'utf8')
if (!stageFile.includes('glass') || !stageFile.includes('CinematicSubtitle')) {
  throw new Error('stage visual contract missing glass or subtitle usage')
}
```

- [ ] **Step 2: Run test to verify it fails (if visual classes missing)**

Run: `cd medical-mp && node scripts/verify-stage-config.mjs`
Expected: FAIL until style contract snippets are present

- [ ] **Step 3: Add final glass/fade/scale style contract in stage and cards**

```css
/* index.vue style snippet */
.glass {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  backdrop-filter: blur(20px);
}
.fade-scale-enter-active,
.fade-scale-leave-active {
  transition: opacity 260ms ease, transform 260ms ease;
}
.fade-scale-enter-from,
.fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
```

- [ ] **Step 4: Run full verification**

Run:
- `cd medical-mp && node scripts/verify-stage-config.mjs`
- `cd medical-mp && node scripts/verify-stage-state-machine.mjs`
- `cd medical-mp && npm run build:mp-weixin`

Expected:
- all verifiers PASS
- build PASS

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/pages/stage/index.vue medical-mp/src/components/DoctorCard.vue medical-mp/src/components/AppointmentCard.vue medical-mp/scripts/verify-stage-config.mjs
git commit -m "feat: finalize immersive glass visuals and transition contract"
```

---

## Spec Coverage Self-Check

- 单主舞台架构：Task 1, Task 3
- 三场景主链路：Task 3, Task 5
- 打字/说/点气泡并行：Task 4
- 医生推荐与预约舞台内闭环：Task 5
- 系统抽屉：Task 6
- 暗色玻璃 + Fade/Scale 动效：Task 1, Task 7
- 兼容旧入口与不改后端契约：Task 5, Task 6

无 `TODO/TBD/implement later` 占位符；每个任务均含可执行命令与预期结果。
