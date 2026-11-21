<template>
  <div class="welcome-page">
    <div class="welcome-bg">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>
    <div class="welcome-content">
      <div class="welcome-logo">
        <svg viewBox="0 0 48 48" width="48" height="48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="48" height="48" rx="12" fill="url(#logo-grad)"/>
          <path d="M14 20l6-6 6 6M14 28l6 6 6-6" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.7"/>
          <path d="M26 16l8 8-8 8" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <defs>
            <linearGradient id="logo-grad" x1="0" y1="0" x2="48" y2="48">
              <stop offset="0%" stop-color="#6366f1"/>
              <stop offset="100%" stop-color="#3b82f6"/>
            </linearGradient>
          </defs>
        </svg>
      </div>
      <h1 class="welcome-title">欢迎使用</h1>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'welcomePage' })
</script>

<style scoped>
.welcome-page {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    height: 100vh;
    overflow: hidden;
    background: linear-gradient(145deg, #f8fafc 0%, #eef2f7 50%, #e8edf5 100%);
}

.welcome-bg {
    position: absolute;
    inset: 0;
    pointer-events: none;
    overflow: hidden;
}

.bg-circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.08;
}

.bg-circle-1 {
    width: 400px;
    height: 400px;
    top: -120px;
    right: -80px;
    background: linear-gradient(135deg, #6366f1, #3b82f6);
}

.bg-circle-2 {
    width: 300px;
    height: 300px;
    bottom: -100px;
    left: -60px;
    background: linear-gradient(135deg, #8b5cf6, #6366f1);
}

.bg-circle-3 {
    width: 200px;
    height: 200px;
    top: 50%;
    left: 60%;
    background: linear-gradient(135deg, #06b6d4, #3b82f6);
}

.welcome-content {
    position: relative;
    z-index: 1;
    text-align: center;
    padding: 40px;
}

.welcome-logo {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 8px 32px rgba(99, 102, 241, 0.12), 0 2px 8px rgba(0, 0, 0, 0.04);
    margin-bottom: 32px;
    backdrop-filter: blur(8px);
}

.welcome-title {
    font-size: 28px;
    font-weight: 600;
    color: #1e293b;
    margin: 0 0 12px;
    letter-spacing: -0.5px;
}
</style>
