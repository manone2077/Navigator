<script setup>

import {onMounted, ref, watch} from "vue";
import Site from "@/components/Site.vue";
import {ElMessage} from "element-plus";
import {Plus} from "@element-plus/icons-vue";
import http from '@/service/http.js'
import {VueDraggable} from "vue-draggable-plus";

const siteInfo = ref([])
const deleteConfirm = ref(false)

watch(siteInfo, async (newSiteInfo, oldSiteInfo) => {
  if (oldSiteInfo.length === newSiteInfo.length ) {
    console.log('拖拽位置')

  }
})

const init = () => {
  http.get("/all")
      .then(res => {
        siteInfo.value = res.data.data
      })
}

onMounted(() => {
  init()
})

let site = ref({
  'id': null,
  'title': '',
  'content': '',
  'url': '',

  'description': '',
  'checkinUrl': '',
  'icon': '',
  'status': -1
})
const dialogVisible = ref(false)

const checkin = () => {
  ElMessage({
    grouping: true,
    type: "success",
    message: "签到成功"
  })
}

const deleteAll = () => {
  deleteConfirm.value = true
  http.post("/deleteAll").then(res => {
    if (res.data.code === 200) {
      ElMessage.success("删除所有站点成功")
      deleteConfirm.value = false
      init()
    }
  })
}

const addSite = () => {
  dialogVisible.value = true
  site.value.id = null
  site.value.title = ''
  site.value.content = ''
  site.value.url = ''

  site.value.description = ''
  site.value.checkinUrl = ''
  site.value.icon = ''
}

const handleClose = () => {
  console.log('do nothing')
}

const handleAdd = () => {
  if (!site.value.url.startsWith('http')) {
    ElMessage.warning('请输入完整url 地址，须包含http 或 https')
    return;
  }
  http.post("/add", site.value)
      .then(res => {
        console.log('添加成功')
        init()
        dialogVisible.value = false

      }).catch(err => {
    console.log('添加失败')
  })
}

const updateInfo = (id) => {
  dialogVisible.value = true
  site.value = siteInfo.value.find((item) => item.id === id)

}

const speedBtn = ref(false)
const speedtest = () => {
  if (!siteInfo.value.length <= 0) {
    speedBtn.value = true
    http.post("/speedtest")
        .then(res => {
          speedBtn.value = true
          init()
        }).finally(result => {
      speedBtn.value = false
      ElMessage.success('测速完成')
    })
  } else {
    ElMessage.warning('请先添加站点哟')
  }
}

</script>

<template>
  <div id="header">
    <h1>皮特 站点导航</h1>
    <el-button @click="checkin" type="primary">一键签到</el-button>
    <el-button @click="speedtest" type="primary" :loading="speedBtn">速度测试</el-button>
    <el-button @click="siteInfo.length<1?ElMessage.warning('没有站点可删除。'):deleteConfirm=true" type="primary">
      删除所有站点
    </el-button>
  </div>
  <div id="main">
    <VueDraggable ref="el" v-model="siteInfo" id="drag" :animation="150">
      <Site v-for="site in siteInfo" :key="site.id" v-bind="site" class="site-item" @update-site="updateInfo"/>
    </VueDraggable>
  </div>

  <div id="add-site">
    <el-button type="primary" @click="addSite">
      <el-icon>
        <Plus/>
      </el-icon>
    </el-button>
  </div>

  <div id="mydialog">
    <el-dialog
        v-model="deleteConfirm"
        title="确认操作"
        width="400"
        :show-close="false"
        :before-close="handleClose"
    >
      <span>是否确认删除所有站点？</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="deleteConfirm = false">取消</el-button>
          <el-button type="danger" @click="deleteAll">
            确认
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
        v-model="dialogVisible"
        title="添加站点"
        width="30%"
        :show-close="false"
        :before-close="handleClose"
        @keyup.esc="dialogVisible=false"
        @keyup.enter="handleAdd"
    >
      <template #footer>
        <div class="dialog-footer">
          <div id="dialog-item">
            <span>站点名称</span>
            <el-input v-model="site.title" style="width: 80%" placeholder="请输入站点名称"/>
            <br>
            <span>站点内容</span>
            <el-input v-model="site.content" style="width: 80%" placeholder="请输入站点内容"/>
            <br>
            <span>站点地址</span>
            <el-input v-model="site.url" style="width: 80%" placeholder="请输入站点地址"/>
            <br>
          </div>
          <el-button @click="dialogVisible=false">取消</el-button>
          <el-button @click="handleAdd" type="primary">
            确认
          </el-button>
        </div>
      </template>

    </el-dialog>
  </div>
</template>

<style scoped>
#header {
  padding: 0 20px;
}

#main > #drag {
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
}

.site-item {
  margin-bottom: 15px;
  margin-right: 5px;
}

.el-button {
  margin-bottom: 20px;
}

#add-site > .el-button {
  position: absolute;
  font-size: 60px;
  height: 60px;
  width: 60px;
  bottom: 50px;
  right: 30px;

  border-radius: 50%;
}

.dialog-footer {
  text-align: center;
}

.dialog-footer > .el-button {
  margin-top: 10px;
}

.el-input {
  margin-top: 5px;
  margin-left: 10px;
}

#dialog-item span {
  line-height: 32px;
}

@media screen and (max-width: 800px) {
  :deep(.el-dialog) {
    width: 90%;
  }
}
</style>