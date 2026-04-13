# Live2D Asset Protection and Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `live2d.zhuyougu.cn` from the retired H5 page into a token-protected Live2D model asset domain, and unify the digital human display name as `安禾`.

**Architecture:** Keep `live2d.zhuyougu.cn` as an independent asset domain, but place it behind nginx `auth_request` validation against a lightweight gateway endpoint that reuses the existing Sa-Token login session. On the mini-program side, switch all Live2D asset loads to authenticated `wx.request` fetches, including textures, because `image.src = https://...` cannot attach auth headers.

**Tech Stack:** uni-app mini-program, WeChat `wx.request`, Spring Cloud Gateway, Sa-Token, nginx, Docker Compose

---

## File Map

### Backend / gateway
- Create: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/controller/Live2dAuthController.java`
- Modify: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/filter/AuthFilter.java`
- Verify: `medical-ai/medical-gateway/src/main/resources/application.yml`

### Asset domain / deployment
- Modify: `medical-mp/live2d-h5/Dockerfile`
- Modify: `medical-mp/live2d-h5/nginx.conf`
- Modify: `medical-ai/docker/docker-compose.yml`
- Optional cleanup check: `medical-mp/live2d-h5/package.json`

### Mini-program frontend
- Modify: `medical-mp/src/lib/cubism-renderer.js`
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Optional helper split if file grows too large: `medical-mp/src/lib/live2d-asset-loader.js`

### Verification
- Verify build output: `medical-mp/dist/build/mp-weixin/**`
- Verify deployment behavior with `curl` against `https://live2d.zhuyougu.cn`

---

### Task 1: Add a gateway auth-check endpoint for nginx `auth_request`

**Files:**
- Create: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/controller/Live2dAuthController.java`
- Modify: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/filter/AuthFilter.java`
- Verify: `medical-ai/medical-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create the lightweight auth-check controller**

```java
package com.medical.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Live2dAuthController {

    @GetMapping("/internal/live2d/check")
    public ResponseEntity<Void> check() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

- [ ] **Step 2: Keep the endpoint protected by the existing global auth filter**

`AuthFilter.java` should continue to require login for `/internal/live2d/check`, so do **not** add it to `.addExclude(...)`.

Expected protected routes block remains conceptually like this:

```java
return new SaReactorFilter()
        .addInclude("/**")
        .addExclude(
                "/api/user/auth/login",
                "/api/user/auth/register",
                "/api/user/auth/wx-login",
                "/api/ai/chat/tts/**"
        )
        .setAuth(obj -> SaRouter.match("/**", StpUtil::checkLogin));
```

- [ ] **Step 3: Verify the gateway route config does not swallow `/internal/live2d/check`**

`application.yml` should keep the path local to the gateway application and not add a conflicting route. The verification target is that no new `spring.cloud.gateway.routes` entry is added for `/internal/live2d/check`.

- [ ] **Step 4: Build the gateway module**

Run:

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-ai
mvn -q -DskipTests -pl medical-gateway -am package
```

Expected: build exits `0` and produces the gateway jar without compilation errors.

- [ ] **Step 5: Commit the backend auth-check change**

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
git add medical-ai/medical-gateway/src/main/java/com/medical/gateway/controller/Live2dAuthController.java \
        medical-ai/medical-gateway/src/main/java/com/medical/gateway/filter/AuthFilter.java \
        medical-ai/medical-gateway/src/main/resources/application.yml
git commit -m "feat: add live2d asset auth check endpoint"
```

---

### Task 2: Turn the retired H5 container into a protected static asset service

**Files:**
- Modify: `medical-mp/live2d-h5/Dockerfile`
- Modify: `medical-mp/live2d-h5/nginx.conf`
- Modify: `medical-ai/docker/docker-compose.yml`
- Verify: `medical-mp/static/models/doctor/**`

- [ ] **Step 1: Change the Live2D Docker image to ship static model assets, not a Vite-built H5 app**

`medical-mp/live2d-h5/Dockerfile` should become an nginx-only image that copies model assets from the mini-program source tree.

```dockerfile
FROM docker.m.daocloud.io/nginx:alpine
COPY static/models /srv/live2d-assets/models
COPY live2d-h5/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8090
CMD ["nginx", "-g", "daemon off;"]
```

- [ ] **Step 2: Update docker-compose so the Docker build context can see `static/models`**

Replace the current service build block:

```yaml
  live2d-h5:
    build: ../../medical-mp/live2d-h5
```

with:

```yaml
  live2d-h5:
    build:
      context: ../../medical-mp
      dockerfile: live2d-h5/Dockerfile
```

- [ ] **Step 3: Replace the old SPA nginx config with auth-gated asset serving**

`medical-mp/live2d-h5/nginx.conf` should stop serving `index.html` and instead gate `/models/` through the gateway.

```nginx
server {
    listen 8090;
    server_name localhost;
    charset utf-8;
    resolver 127.0.0.11 valid=1s ipv6=off;
    resolver_timeout 5s;

    location = / {
        return 404;
    }

    location /models/ {
        root /srv/live2d-assets;
        auth_request /_auth/live2d;
        try_files $uri =404;
        add_header Cache-Control "private, max-age=300";
    }

    location = /_auth/live2d {
        internal;
        proxy_pass http://medical-gateway:8080/internal/live2d/check;
        proxy_pass_request_body off;
        proxy_set_header Content-Length "";
        proxy_set_header Authorization $http_authorization;
        proxy_set_header X-Original-URI $request_uri;
    }
}
```

- [ ] **Step 4: Verify the old H5 page is gone by config, not by convention**

Run:

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
grep -n "index.html\|try_files .*index.html" medical-mp/live2d-h5/nginx.conf
```

Expected: no active `index.html` fallback remains in the live2d nginx config.

- [ ] **Step 5: Rebuild and restart only the gateway + live2d asset service**

Run:

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-ai/docker
docker compose up -d --build gateway live2d-h5
```

Expected: `medical-gateway` and `medical-live2d-h5` both reach `Up` state.

- [ ] **Step 6: Verify unauthenticated asset access is denied**

Run:

```bash
curl -I https://live2d.zhuyougu.cn/models/doctor/wariza.model3.json
```

Expected: `401` or `403`, not `200`.

- [ ] **Step 7: Commit the asset-domain deployment change**

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
git add medical-mp/live2d-h5/Dockerfile \
        medical-mp/live2d-h5/nginx.conf \
        medical-ai/docker/docker-compose.yml
git commit -m "feat: protect live2d asset domain with gateway auth"
```

---

### Task 3: Make all mini-program Live2D asset requests send the login token

**Files:**
- Modify: `medical-mp/src/lib/cubism-renderer.js`
- Optional split if needed: `medical-mp/src/lib/live2d-asset-loader.js`

- [ ] **Step 1: Add a reusable auth-header helper for Live2D asset requests**

Near the top of `cubism-renderer.js`, add a helper that matches the existing app token convention.

```js
const getLive2dAuthHeader = () => {
  const token = uni.getStorageSync('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}
```

- [ ] **Step 2: Pass auth headers in all `wx.request` model fetches**

Update the raw HTTP helper so JSON, moc3, motion, expression, and physics requests all carry the token.

```js
const httpGet = (url, responseType = 'text') =>
  new Promise((resolve, reject) => {
    wx.request({
      url,
      method: 'GET',
      responseType,
      header: {
        ...getLive2dAuthHeader()
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${url}`))
        }
      },
      fail: reject
    })
  })
```

- [ ] **Step 3: Stop using remote `image.src = https://...` for protected textures**

Because `canvas.createImage().src` cannot attach `Authorization` headers, replace direct remote texture loading with authenticated download-to-temp-file loading.

```js
const downloadProtectedAsset = (url) =>
  new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header: {
        ...getLive2dAuthHeader()
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.tempFilePath) {
          resolve(res.tempFilePath)
        } else {
          reject(new Error(`DOWNLOAD ${res.statusCode}: ${url}`))
        }
      },
      fail: reject
    })
  })

const loadImage = async (canvas, imageUrl) => {
  const tempFilePath = await downloadProtectedAsset(imageUrl)
  return new Promise((resolve, reject) => {
    const image = createCanvasImage(canvas)
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = tempFilePath
  })
}
```

- [ ] **Step 4: Emit clearer authorization errors for Live2D loads**

When `loadModel()` catches a `401/403`, convert it into a diagnostic that distinguishes auth failure from generic load failure.

```js
.catch((err) => {
  const message = String(err?.message || err)
  if (message.includes('HTTP 401') || message.includes('HTTP 403') || message.includes('DOWNLOAD 401') || message.includes('DOWNLOAD 403')) {
    console.error('[Live2D] 模型资源未授权，请检查登录态或资源域鉴权配置', err)
  } else {
    console.error('[Live2D] 模型资源加载失败', err)
  }
  throw err
})
```

- [ ] **Step 5: Build the mini-program**

Run:

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected: build exits `0` and the generated mini-program still contains `pages/chat/chat.js` plus the updated asset-loading logic.

- [ ] **Step 6: Commit the authenticated asset-loader change**

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
git add medical-mp/src/lib/cubism-renderer.js
git commit -m "feat: authenticate live2d asset requests in mini program"
```

---

### Task 4: Unify the digital human name as `安禾`

**Files:**
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Optional verify: `medical-mp/src/components/ChatMessage.vue`

- [ ] **Step 1: Add an explicit visible name label in the Live2D overlay**

In `chat.vue`, add a title area so the nurse name is visible instead of existing only in internal copy.

```vue
<view class="live2d-overlay">
  <view class="assistant-badge">
    <text class="assistant-name">安禾</text>
    <text class="assistant-role">智能护士</text>
  </view>
  <view class="status-bar">
    <view class="status-dot" :class="{ active: isThinking }"></view>
    <text class="status-text">{{ statusText }}</text>
  </view>
</view>
```

- [ ] **Step 2: Set the default service copy to include the confirmed name**

Update the initial status text from:

```js
const statusText = ref('正在为您服务...')
```

to:

```js
const statusText = ref('安禾正在为您服务...')
```

- [ ] **Step 3: Add minimal styles for the name badge**

Append styles in `chat.vue` so the name is consistently visible in both devtools and device builds.

```css
.assistant-badge {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.assistant-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

.assistant-role {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.85);
}
```

- [ ] **Step 4: Rebuild and smoke-check the UI copy**

Run:

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
grep -n "安禾" dist/build/mp-weixin/pages/chat/chat.js
```

Expected: the built chat page contains `安禾`.

- [ ] **Step 5: Commit the naming update**

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
git add medical-mp/src/pages/chat/chat.vue
git commit -m "feat: name the digital nurse Anhe"
```

---

### Task 5: End-to-end verification and deployment handoff

**Files:**
- Verify: `medical-ai/docker/docker-compose.yml`
- Verify: `medical-mp/dist/build/mp-weixin/**`
- Verify runtime behavior against `https://live2d.zhuyougu.cn`

- [ ] **Step 1: Verify unauthenticated requests are blocked**

Run:

```bash
curl -I https://live2d.zhuyougu.cn/models/doctor/wariza.model3.json
curl -I https://live2d.zhuyougu.cn/
```

Expected:
- model file returns `401` or `403`
- domain root returns `404`

- [ ] **Step 2: Verify authenticated requests succeed with a real login token**

Run a real logged-in request from the mini-program or devtools network panel, then confirm at least these resources return `200`:
- `/models/doctor/wariza.model3.json`
- `/models/doctor/Wariza.moc3`
- `/models/doctor/Wariza.4096/texture_00.png`

Expected: all required resources load without anonymous public access.

- [ ] **Step 3: Verify the live model still renders in the mini-program**

Manual checks in WeChat DevTools and one real device run:
- open the chat page after login
- confirm the model appears
- confirm textures are visible, not blank silhouettes
- confirm console does not log `模型资源未授权`

- [ ] **Step 4: Verify the naming is visible in the UI**

Manual checks:
- the overlay shows `安禾`
- the default status text contains `安禾`
- no old H5-only branding is still visible on the asset domain

- [ ] **Step 5: Final integration commit if verification required follow-up tweaks**

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
git status --short
```

Expected: either a clean tree, or one final integration commit after last-mile fixes.

---

## Self-Review

### Spec coverage
- Asset domain no longer serves H5 page: covered by Task 2
- Token-gated model access using existing login token: covered by Tasks 1, 2, 3
- Frontend sends token for all model asset types: covered by Task 3
- Naming unified to `安禾`: covered by Task 4
- Verification on blocked anonymous access and successful logged-in rendering: covered by Task 5

### Placeholder scan
- No `TODO` / `TBD` markers remain.
- Commands and target files are explicit.
- Critical image-auth limitation is handled explicitly instead of left implicit.

### Type consistency
- Gateway validation endpoint path is consistently `/internal/live2d/check`.
- Asset domain path is consistently `/models/**`.
- Frontend uses the same `Authorization: Bearer <token>` convention as existing API requests.
