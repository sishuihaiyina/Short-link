<template>
  <div style="width: 100%; display: flex; flex-direction: column;">
    <div class="title-box">
      <span>{{ title }}</span>
      <slot name="titleButton">
      </slot>
    </div>
    <div class="content-box">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
defineProps({
  title: {
    type: String,
    default: '默认标题'
  }
})
const emits = defineEmits(['onMounted'])
onMounted(() => {
  emits('onMounted')
})
</script>

<style lang="scss" scoped>
.title-box {
  display: flex;
  position: relative;
  justify-content: space-between;
  align-items: center;
  height: 44px;
  border-bottom: 1px solid var(--sl-border);
  padding: 0 4px;
  color: var(--sl-text);
  font-size: 15px;
  font-weight: 600;
}
.title-box::before {
  content: '';
  position: absolute;
  height: 2px;
  width: 100px;
  left: 0;
  background-color: var(--sl-primary);
  z-index: 99;
  bottom: -1px;
}
.content-box {
  flex: 1;
}
</style>