<template>
  <view class="slot-picker">
    <scroll-view class="date-tabs" scroll-x>
      <view
        v-for="date in dates"
        :key="date"
        class="date-tab"
        :class="{ active: date === activeDate }"
        @click="activeDate = date"
      >
        {{ date }}
      </view>
    </scroll-view>

    <view class="period-block">
      <text class="period-title">上午</text>
      <view class="grid">
        <view
          v-for="slot in morningSlots"
          :key="slot.slotId"
          class="slot-item"
          :class="{ selected: slot.slotId === selectedSlotId }"
          @click="selectSlot(slot)"
        >
          <text class="time">{{ slot.time }}</text>
          <text class="remaining">剩余{{ slot.remaining }}</text>
        </view>
      </view>
      <text v-if="morningSlots.length === 0" class="empty">暂无号源</text>
    </view>

    <view class="period-block">
      <text class="period-title">下午</text>
      <view class="grid">
        <view
          v-for="slot in afternoonSlots"
          :key="slot.slotId"
          class="slot-item"
          :class="{ selected: slot.slotId === selectedSlotId }"
          @click="selectSlot(slot)"
        >
          <text class="time">{{ slot.time }}</text>
          <text class="remaining">剩余{{ slot.remaining }}</text>
        </view>
      </view>
      <text v-if="afternoonSlots.length === 0" class="empty">暂无号源</text>
    </view>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 特殊消息卡片组件在 Gemini 不可用时由 Codex 接管实现。
import { computed, ref, watch } from 'vue'

interface Slot {
  date: string
  period: string
  time: string
  remaining: number
  slotId: number | string
}

const props = defineProps<{
  slots: Slot[]
}>()

const emit = defineEmits<{
  (e: 'select', slot: Slot): void
}>()

const dates = computed(() => Array.from(new Set(props.slots.map((item) => item.date))))
const activeDate = ref('')
const selectedSlotId = ref<number | string | null>(null)

watch(
  dates,
  (val) => {
    if (!val.length) {
      activeDate.value = ''
      return
    }
    if (!val.includes(activeDate.value)) {
      activeDate.value = val[0]
    }
  },
  { immediate: true }
)

const filteredSlots = computed(() =>
  props.slots.filter((item) => item.date === activeDate.value)
)

const morningSlots = computed(() =>
  filteredSlots.value.filter((item) => ['morning', 'am', '上午'].includes(item.period.toLowerCase?.() || item.period))
)

const afternoonSlots = computed(() =>
  filteredSlots.value.filter((item) => ['afternoon', 'pm', '下午'].includes(item.period.toLowerCase?.() || item.period))
)

const selectSlot = (slot: Slot) => {
  selectedSlotId.value = slot.slotId
  emit('select', slot)
}
</script>

<style scoped>
.slot-picker {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 20rpx;
  box-shadow: 0 6rpx 18rpx rgba(74, 144, 217, 0.12);
}

.date-tabs {
  white-space: nowrap;
  margin-bottom: 16rpx;
}

.date-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 160rpx;
  padding: 16rpx 20rpx;
  margin-right: 12rpx;
  border-radius: 999rpx;
  background: #f5f7fa;
  color: #606266;
  font-size: 24rpx;
}

.date-tab.active {
  background: #4a90d9;
  color: #ffffff;
}

.period-block {
  margin-top: 14rpx;
}

.period-title {
  display: block;
  font-size: 26rpx;
  color: #303133;
  margin-bottom: 10rpx;
  font-weight: 600;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
}

.slot-item {
  border: 2rpx solid #e4e7ed;
  border-radius: 10rpx;
  padding: 14rpx;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.slot-item.selected {
  border-color: #4a90d9;
  background: #ecf5ff;
}

.time {
  color: #303133;
  font-size: 26rpx;
  font-weight: 600;
}

.remaining {
  color: #67c23a;
  font-size: 22rpx;
}

.empty {
  color: #909399;
  font-size: 24rpx;
}
</style>
