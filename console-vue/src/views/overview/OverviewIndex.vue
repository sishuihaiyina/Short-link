<template>
  <div class="overview-page">
    <!-- 顶部：时间范围 -->
    <div class="page-head">
      <div>
        <div class="page-title">概览</div>
        <div class="page-sub">当前账号下所有分组的访问汇总</div>
      </div>
      <div class="head-actions">
        <el-date-picker
          v-model="range"
          type="daterange"
          unlink-panels
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
          @change="loadOverview"
        />
        <el-button :loading="loading" @click="loadOverview">刷新</el-button>
      </div>
    </div>

    <!-- 指标卡片 -->
    <div class="stat-grid">
      <div class="stat-card" v-for="item in statCards" :key="item.label">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
      </div>
    </div>

    <!-- 死信队列 -->
    <div class="panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">统计消息死信</div>
          <div class="panel-sub">RocketMQ 重试 5 次仍失败的消息会落到这里，可手动重放</div>
        </div>
        <el-button :loading="dlqLoading" size="small" @click="loadDlq">刷新</el-button>
      </div>
      <el-table :data="dlq.records" v-loading="dlqLoading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="fullShortUrl" label="短链接" min-width="220" show-overflow-tooltip />
        <el-table-column prop="eventId" label="事件 ID" min-width="220" show-overflow-tooltip />
        <el-table-column label="落库时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.replayed === 1" type="info" size="small">已重放</el-tag>
            <el-tag v-else type="warning" size="small">待处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.replayed === 1"
              @click="replay(row)"
            >重放</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div style="padding: 24px 0; color: #8a919e">暂无死信消息</div>
        </template>
      </el-table>
      <div class="panel-pager">
        <el-pagination
          v-model:current-page="dlq.current"
          v-model:page-size="dlq.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="dlq.total"
          @size-change="loadDlq"
          @current-change="loadDlq"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const { proxy } = getCurrentInstance()
const API = proxy.$API

const formatDate = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// 默认近 7 天
const end = new Date()
const start = new Date()
start.setDate(start.getDate() - 6)
const range = ref([formatDate(start), formatDate(end)])

const loading = ref(false)
const overview = ref({})

const statCards = computed(() => [
  { label: '链接总数', value: overview.value.totalLinks ?? 0 },
  { label: '访问次数', value: overview.value.pv ?? 0 },
  { label: '访问人数', value: overview.value.uv ?? 0 },
  { label: 'IP 数', value: overview.value.uip ?? 0 }
])

const loadOverview = async () => {
  if (!range.value || range.value.length !== 2) {
    return
  }
  loading.value = true
  try {
    const res = await API.smallLinkPage.queryOverview({
      startDate: range.value[0],
      endDate: range.value[1]
    })
    if (res?.data?.code === '0') {
      overview.value = res.data.data || {}
      if (overview.value.failedGroups) {
        ElMessage.warning(`有 ${overview.value.failedGroups} 个分组统计查询失败，结果可能偏低`)
      }
    } else {
      ElMessage.error(res?.data?.message || '概览加载失败')
    }
  } catch (e) {
    ElMessage.error('概览加载失败')
  } finally {
    loading.value = false
  }
}

const dlqLoading = ref(false)
const dlq = reactive({ records: [], total: 0, current: 1, size: 10 })

const formatTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

const loadDlq = async () => {
  dlqLoading.value = true
  try {
    const res = await API.mq.pageDlq({ current: dlq.current, size: dlq.size })
    if (res?.data?.code === '0') {
      const page = res.data.data || {}
      dlq.records = page.records || []
      dlq.total = page.total || 0
    }
  } catch (e) {
    // 死信属于附加信息，加载失败不打断页面
  } finally {
    dlqLoading.value = false
  }
}

const replay = async (row) => {
  try {
    const res = await API.mq.replayDlq(row.id)
    if (res?.data?.code === '0') {
      ElMessage.success('已重新投递到统计队列')
      loadDlq()
    } else {
      ElMessage.error(res?.data?.message || '重放失败')
    }
  } catch (e) {
    ElMessage.error('重放失败')
  }
}

onMounted(() => {
  loadOverview()
  loadDlq()
})
</script>

<style lang="scss" scoped>
.overview-page {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
  background-color: var(--sl-bg);
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--sl-text);
}

.page-sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--sl-text-muted);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background-color: var(--sl-surface);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  box-shadow: var(--sl-shadow-sm);
  padding: 18px 20px;
}

.stat-label {
  font-size: 13px;
  color: var(--sl-text-muted);
}

.stat-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  color: var(--sl-text);
}

.panel {
  background-color: var(--sl-surface);
  border: 1px solid var(--sl-border);
  border-radius: var(--sl-radius);
  box-shadow: var(--sl-shadow-sm);
  padding: 18px 20px 12px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text);
}

.panel-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sl-text-muted);
}

.panel-pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
