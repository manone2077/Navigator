<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, MagicStick, RefreshLeft, Upload } from '@element-plus/icons-vue'
import { addSite, fetchIcon, siteMeta, updateSite, uploadImage } from '@/service/api.js'
import { avatarColor, faviconOf, normalizeUrl } from '@/composables/site.js'

const emit = defineEmits(['saved'])

const visible = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fetching = ref(false)
const formRef = ref()
const urlInput = ref()
const fileInput = ref()
const iconFailed = ref(false)

const emptySite = () => ({ id: null, title: '', url: '', content: '', description: '', checkinUrl: '', icon: '' })
const form = reactive(emptySite())
const isEdit = computed(() => form.id != null)

const rules = {
  title: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入站点地址', trigger: 'blur' },
    {
      trigger: 'blur',
      validator: (_, value, cb) => {
        try {
          const u = new URL(value)
          if (!/^https?:$/.test(u.protocol)) throw new Error()
          cb()
        } catch {
          cb(new Error('站点地址格式不正确'))
        }
      },
    },
  ],
}

const previewIcon = computed(() => form.icon || faviconOf(form.url))

/** 打开对话框；传入站点则为编辑（使用副本，取消时不会修改列表数据） */
function open(site) {
  Object.assign(form, emptySite(), site ? JSON.parse(JSON.stringify(site)) : {})
  autoTitle = ''
  lastMetaUrl = ''
  iconFailed.value = false
  visible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

// 对话框内容首次打开时才渲染，需在动画结束后再聚焦
function onOpened() {
  ;(isEdit.value ? null : urlInput.value)?.focus()
}

// 自动填写的名称（用户手动修改后不再覆盖）
let autoTitle = ''
let lastMetaUrl = ''
const metaLoading = ref(false)

async function onUrlBlur() {
  form.url = normalizeUrl(form.url)
  iconFailed.value = false
  let host = ''
  try {
    host = new URL(form.url).hostname.replace(/^www\./, '')
  } catch {
    return
  }
  // 未填写名称时，先用域名作为默认名称
  if (!form.title) form.title = autoTitle = host
  // 添加站点时，自动获取页面标题和图标
  if (isEdit.value || form.url === lastMetaUrl) return
  lastMetaUrl = form.url
  metaLoading.value = true
  try {
    const meta = await siteMeta(form.url)
    if (form.url !== lastMetaUrl || !visible.value) return
    if (meta.title && (!form.title || form.title === autoTitle)) form.title = autoTitle = meta.title
    if (meta.icon && !form.icon) form.icon = meta.icon
  } catch {
    /* 获取失败不影响手动填写 */
  } finally {
    metaLoading.value = false
  }
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    form.icon = await uploadImage(file)
    iconFailed.value = false
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    uploading.value = false
  }
}

async function autoFetchIcon() {
  form.url = normalizeUrl(form.url)
  if (!form.url) {
    ElMessage.warning('请先填写站点地址')
    return
  }
  fetching.value = true
  try {
    form.icon = await fetchIcon(form.url)
    iconFailed.value = false
    ElMessage.success('图标获取成功')
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    fetching.value = false
  }
}

async function submit() {
  form.url = normalizeUrl(form.url)
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await (isEdit.value ? updateSite({ ...form }) : addSite({ ...form }))
    ElMessage.success(res.msg)
    visible.value = false
    emit('saved', res.data, !isEdit.value)
  } catch {
    /* 已在拦截器中提示 */
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑站点' : '添加站点'"
    width="480px"
    class="site-dialog"
    align-center
    :close-on-click-modal="false"
    @opened="onOpened"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="站点地址" prop="url">
        <el-input
          ref="urlInput"
          v-model="form.url"
          placeholder="例如 192.168.1.2:8080 或 example.com"
          clearable
          @blur="onUrlBlur"
          @keyup.enter="submit"
        >
          <template #suffix>
            <el-tooltip v-if="metaLoading" content="正在获取站点名称和图标" placement="top">
              <el-icon class="is-loading"><Loading /></el-icon>
            </el-tooltip>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="站点名称" prop="title">
        <el-input v-model="form.title" placeholder="请输入站点名称" maxlength="50" clearable @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="站点描述" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="可选，简单描述一下这个站点"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="站点图标">
        <div class="icon-row">
          <div class="icon-preview" :class="{ 'has-img': previewIcon && !iconFailed }">
            <img v-if="previewIcon && !iconFailed" :src="previewIcon" alt="" @error="iconFailed = true" />
            <span v-else class="letter" :style="{ background: avatarColor(form.title) }">
              {{ (form.title || '?').charAt(0).toUpperCase() }}
            </span>
          </div>
          <div class="icon-tip">
            <div>
              <el-button size="small" :icon="MagicStick" :loading="fetching" @click="autoFetchIcon">自动获取</el-button>
              <el-button size="small" :icon="Upload" :loading="uploading" @click="fileInput.click()">上传图标</el-button>
              <el-button v-if="form.icon" size="small" :icon="RefreshLeft" @click="(form.icon = ''), (iconFailed = false)">
                恢复默认
              </el-button>
            </div>
            <span>填写地址后会自动获取站点名称和图标；获取不到时可手动上传（png/jpg/gif/webp/ico，≤5MB）</span>
          </div>
          <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/x-icon,.ico" hidden @change="onFileChange" />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存' : '添加' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.icon-row {
  display: flex;
  gap: 14px;
  align-items: center;
  width: 100%;
}

.icon-preview {
  flex: none;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: var(--nav-icon-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.icon-preview img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.letter {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 22px;
}

.icon-tip {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
