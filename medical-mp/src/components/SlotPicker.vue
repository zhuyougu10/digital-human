<template>
  <view class="slot-picker">
    <scroll-view class="date-tabs" scroll-x enable-flex>
      <view
        v-for="date in dates"
        :key="date"
        class="date-tab"
        :class="{ active: date === activeDate }"
        @click="activeDate = date"
      >
        <text class="date-text">{{ date }}</text>
      </view>
    </scroll-view>

    <view class="period-block">
      <view class="period-header">
        <view class="period-indicator morning"></view>
        <text class="period-title">上午</text>
      </view>
      <view class="grid">
        <view
          v-for="slot in morningSlots"
          :key="slot.slotId"
          class="slot-item"
          :class="{ 
            selected: slot.slotId === selectedSlotId,
            disabled: slot.remaining <= 0 
          }"
          @click="slot.remaining > 0 && selectSlot(slot)"
        >
          <text class="time">{{ slot.time }}</text>
          <view class="remaining-box">
            <text class="remaining-text">{{ slot.remaining > 0 ? '剩余' + slot.remaining : '约满' }}</text>
          </view>
        </view>
      </view>
      <view v-if="morningSlots.length === 0" class="empty-state">
        <text class="empty-text">该时段暂无号源</text>
      </view>
    </view>

    <view class="period-block">
      <view class="period-header">
        <view class="period-indicator afternoon"></view>
        <text class="period-title">下午</text>
      </view>
      <view class="grid">
        <view
          v-for="slot in afternoonSlots"
          :key="slot.slotId"
          class="slot-item"
          :class="{ 
            selected: slot.slotId === selectedSlotId,
            disabled: slot.remaining <= 0 
          }"
          @click="slot.remaining > 0 && selectSlot(slot)"
        >
          <text class="time">{{ slot.time }}</text>
          <view class="remaining-box">
            <text class="remaining-text">{{ slot.remaining > 0 ? '剩余' + slot.remaining : '约满' }}</text>
          </view>
        </view>
      </view>
      <view v-if="afternoonSlots.length === 0" class="empty-state">
        <text class="empty-text">该时段暂无号源</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
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
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.date-tabs {
  display: flex;
  white-space: nowrap;
  margin-bottom: 32rpx;
  padding-bottom: 8rpx;
}

.date-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 180rpx;
  height: 72rpx;
  padding: 0 24rpx;
  margin-right: 16rpx;
  border-radius: 36rpx;
  background: #f1f5f9;
  transition: all 0.2s;
  flex-shrink: 0;
}

.date-tab.active {
  background: #2563eb;
  box-shadow: 0 4rpx 12rpx rgba(37, 99, 235, 0.2);
}

.date-text {
  font-size: 26rpx;
  color: #64748b;
  font-weight: 500;
}

.date-tab.active .date-text {
  color: #ffffff;
}

.period-block {
  margin-top: 24rpx;
}

.period-header {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
}

.period-indicator {
  width: 6rpx;
  height: 24rpx;
  border-radius: 3rpx;
  margin-right: 12rpx;
}

.period-indicator.morning { background: #f59e0b; }
.period-indicator.afternoon { background: #2563eb; }

.period-title {
  font-size: 28rpx;
  color: #1e293b;
  font-weight: 600;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.slot-item {
  border: 2rpx solid #f1f5f9;
  border-radius: 16rpx;
  padding: 20rpx;
  background: #f8faff;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  transition: all 0.2s;
}

.slot-item.selected {
  border-color: #2563eb;
  background: #eff6ff;
}

.slot-item.disabled {
  background: #f1f5f9;
  border-color: #f1f5f9;
  opacity: 0.6;
}

.time {
  color: #334155;
  font-size: 30rpx;
  font-weight: 700;
}

.slot-item.selected .time {
  color: #2563eb;
}

.remaining-box {
  padding: 2rpx 12rpx;
  background: #dcfce7;
  border-radius: 6rpx;
}

.remaining-text {
  color: #059669;
  font-size: 20rpx;
  font-weight: 500;
}

.slot-item.selected .remaining-box {
  background: #2563eb;
}

.slot-item.selected .remaining-text {
  color: #ffffff;
}

.slot-item.disabled .remaining-box {
  background: #e2e8f0;
}

.slot-item.disabled .remaining-text {
  color: #94a3b8;
}

.empty-state {
  padding: 32rpx;
  text-align: center;
}

.empty-text {
  color: #94a3b8;
  font-size: 24rpx;
}
</style>
