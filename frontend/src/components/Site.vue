<script setup>
import {EditPen} from "@element-plus/icons-vue";
import {computed, onMounted, ref, useTemplateRef} from "vue";
import {ElMessage} from "element-plus";
import axios from "axios";
import http from "@/service/http.js";

const props = defineProps(['id', 'content', 'url', 'title', 'description', 'checkinUrl', 'icon', 'status'])

let baseUrl = props['url']
const imageUrl=ref('')

onMounted(()=>{
  if(props.icon.length >0){
    imageUrl.value=props.icon
  }
})

const color = computed(() => {
  if (props.status < 0) {
    return 'gray'
  } else if (props.status < 1000) {
    return 'green'
  } else if (props.status <= 5000) {
    return 'orange'
  } else if (props.status === 88888) {
    return 'brown'
  } else {
    return 'red'
  }
})

const speedTip = computed(() => {
  if (props.status < 0) {
    return '您好没测过速度呢'
  } else if (props.status < 1000) {
    return '速度非常好'
  } else if (props.status <= 5000) {
    return '速度有点慢哟'
  } else if (props.status === 88888) {
    return '超时啦啦啦啦'
  } else if (props.status === 99999) {
    return '网络不可达，拔网线掀桌子吧'
  }
})

const faviconUrl = computed(() => {
  if (baseUrl.endsWith('/')) {
    return baseUrl + 'favicon.ico'

  } else {
    if (!baseUrl.startsWith('http')) {
      baseUrl = 'http://' + baseUrl
    }
    let temp = baseUrl.split('/')
    const schema = temp[0]
    const domain = temp[2]

    return schema + "//" + domain + '/favicon.ico'
  }
})

const upload=useTemplateRef('upload')
const setIconImg=()=>{
  upload.value.click()
}

const uploadImg=()=>{
  let file=upload.value.files[0]
  let formData = new FormData();
  formData.append('file', file);
  axios({
    url: '/api/upload',
    method: 'POST',
    data:formData,
    headers:{
      'Content-Type': 'multipart/form-data',
    }
  }).then(resp=>{
    if(resp.data.code === 200){
      imageUrl.value = '/images/'+resp.data.data;
      console.log(imageUrl.value);

      http.post('/updateImage',{id:props.id,icon:imageUrl.value}).then(res=>{
        if(res.data.code === 200){
          ElMessage.success(res.data.msg)
        }
      })
    }
  })
}



</script>

<template>
  <el-card>
    <div id="header">
      <h2>{{ title }}</h2>
      <img :src="imageUrl.length>0?imageUrl:faviconUrl" alt="FAILED" @click="setIconImg"  />
      <input id="file" ref="upload" type="file" name="file"  value="" @change="uploadImg" style="display: none">

      <span style="display: none">{{ id }}{{ url }}{{ status }}</span>
    </div>
    <p>
      {{ content }}
    </p>
    <div id="footer">
      <span class="link">
        <a :href="url" target="_blank">{{ url }}</a>
      </span>
      <span id="icon">
        <el-tooltip
            class="box-item"
            effect="light"
            :content="speedTip"
            placement="top-start"
        >
         <span id="status"><el-icon><Link/></el-icon></span>

      </el-tooltip>
        <span id="update"><el-icon><EditPen @click="$emit('updateSite',id)"/></el-icon></span>
      </span>
    </div>
  </el-card>
</template>

<style scoped>
#footer .link,h2 ,p{
  width: 80%;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

.el-card {
  width: 30%;
  min-width: 250px;
}

#header, #footer {
  display: flex;
  justify-content: space-between;
}

#header > img {
  width: 40px;
  height: 40px;
  cursor: pointer;
}

#status, #update {
  font-size: 25px;
}

#status {
  margin-right: 10px;
  color: v-bind("color");
}

#status > .el-icon {
  font-weight: bold;
}

@media screen and (max-width: 800px) {
  .el-card {
    width: 100%;
  }
}
</style>