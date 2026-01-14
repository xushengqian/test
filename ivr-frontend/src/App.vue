<script setup>
import { ref, onMounted } from 'vue'

const status = ref('Checking...')

onMounted(async () => {
  try {
    const res = await fetch('/api/status')
    if (res.ok) {
        const data = await res.json()
        status.value = `Connected: ${data.message}`
    } else {
        status.value = 'Error connecting to backend'
    }
  } catch (e) {
    status.value = 'Backend unreachable'
  }
})
</script>

<template>
  <div>
    <h1>IVR System Dashboard</h1>
    <p>Backend Status: {{ status }}</p>
  </div>
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
