<template>
  <router-view />
</template>

<script setup>
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: var(--bg-page);
  color: var(--text-primary);
  scroll-behavior: smooth;
}

::selection {
  background: var(--primary-bg);
  color: var(--primary-color);
}

/* NProgress spinner hide */
#nprogress .spinner {
  display: none !important;
}

#app {
  height: 100vh;
  width: 100%;
}
</style>
