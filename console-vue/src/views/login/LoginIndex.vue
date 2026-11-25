<template>
  <div class="login-page">
    <div ref="vantaRef" class="vanta"></div>
    <div class="login-brand">短链接生成平台</div>
    <div class="login-box">
      <!-- 登录 -->
      <div class="logon" :class="{ hidden: !isLogin }">
        <h2>登录</h2>
        <p class="login-sub">输入用户名和密码进入控制台</p>
        <el-form ref="loginFormRef1" :model="loginForm" label-width="50px" :rules="loginFormRule">
          <div class="form-container1">
            <el-form-item prop="phone">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
                <template v-slot:prepend> 用户名 </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" clearable placeholder="请输入密码" show-password
                style="margin-top: 20px">
                <template v-slot:prepend> 密<span class="second-font">码</span> </template>
              </el-input>
            </el-form-item>
          </div>
          <div class="btn-gourp">
            <div>
              <el-checkbox class="remeber-password" v-model="checked"
                style="color: #a0a0a0; margin: 0 0 0px 0">记住密码</el-checkbox>
            </div>
            <div>
              <el-button :loading="loading" @keyup.enter="login" type="primary"
                @click="login(loginFormRef1)">登录</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <!-- 注册 -->
      <div class="register" :class="{ hidden: isLogin }">
        <h2>注册</h2>
        <p class="login-sub">注册后即可创建和管理短链接</p>
        <el-form ref="loginFormRef2" :model="addForm" label-width="50px" class="form-container" width="width"
          :rules="addFormRule">
          <el-form-item prop="username">
            <el-input v-model="addForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
              <template v-slot:prepend> 用户名 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="mail">
            <el-input v-model="addForm.mail" placeholder="请输入邮箱" show-word-limit clearable>
              <template v-slot:prepend> 邮<span class="second-font">箱</span> </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="addForm.phone" placeholder="请输入手机号" show-word-limit clearable>
              <template v-slot:prepend> 手机号 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="realName">
            <el-input v-model="addForm.realName" placeholder="请输入姓名" show-word-limit clearable>
              <template v-slot:prepend> 姓<span class="second-font">名</span> </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="addForm.password" type="password" clearable placeholder="请输入密码" show-password>
              <template v-slot:prepend> 密<span class="second-font">码</span> </template>
            </el-input>
          </el-form-item>
          <!-- 验证码 -->
          <!-- <el-form-item prop="vertify_code">
            <el-input
              v-model="loginForm.vertify_code"
              placeholder="验证码"
              prefix-icon="el-icon-key"
              clearable
            >
              <template v-slot:append>
                <div class="login-code" @click="refreshCode" title="看不清？点击切换">
                  <vertify-code :identifyCode="loginIdentifyCode"></vertify-code>
                </div>
              </template>
            </el-input>
          </el-form-item> -->
          <div class="btn-gourp">
            <div></div>
            <div>
              <el-button :loading="loading" @keyup.enter="login" type="primary"
                @click="addUser(loginFormRef2)">注册</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <!-- 登录 / 注册切换 -->
      <div class="move" ref="moveRef">
        <span>{{ !isLogin ? '已有账号？' : '还没有账号？' }}</span>
        <el-button link type="primary" @click="changeLogin">{{
          !isLogin ? '去登录' : '去注册'
        }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { setToken, setUsername, getUsername } from '@/core/auth.js'
import { ref, reactive, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as THREE from 'three'
import WAVES from 'vanta/src/vanta.waves'
const { proxy } = getCurrentInstance()
const API = proxy.$API
const loginFormRef1 = ref()
const loginFormRef2 = ref()
const router = useRouter()
const loginForm = reactive({
  username: '',
  password: '',
})
const addForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  mail: ''
})

const addFormRule = reactive({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur'
    },
    { min: 11, max: 11, message: '手机号必须是11位', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
  mail: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      pattern: /^([a-zA-Z]|[0-9])(\w|\-)+@[a-zA-Z0-9]+\.([a-zA-Z]{2,4})$/,
      message: '请输入正确的邮箱号',
      trigger: 'blur'
    }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
  ]
})
const loginFormRule = reactive({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
})
// 注册
const addUser = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (!valid) return false
    try {
      // 检测用户名是否已经存在，后端返回的 data 为 true 表示用户名可用
      const res1 = await API.user.hasUsername({ username: addForm.username })
      if (res1?.data?.data === false) {
        ElMessage.warning('用户名已存在！')
        return
      }
      // 注册
      const res2 = await API.user.addUser(addForm)
      if (res2?.data?.code !== '0') {
        // 例如手机号已存在、用户记录已存在等，直接展示后端返回的原因
        ElMessage.warning(res2?.data?.message || '注册失败')
        return
      }
      const res3 = await API.user.login({ username: addForm.username, password: addForm.password })
      const token = res3?.data?.data?.token
      // 将username和token保存到cookies中和localStorage中
      if (token) {
        setToken(token)
        setUsername(addForm.username)
        localStorage.setItem('token', token)
        localStorage.setItem('username', addForm.username)
      }
      ElMessage.success('注册登录成功！')
      router.push('/home')
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || '注册失败，请稍后重试')
    }
  })
}
// 登录
const login = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (!valid) return false
    try {
      const res1 = await API.user.login(loginForm)
      if (res1?.data?.code === '0') {
        const token = res1?.data?.data?.token
        // 将username和token保存到cookies中和localStorage中
        if (token) {
          setToken(token)
          setUsername(loginForm.username)
          localStorage.setItem('token', token)
          localStorage.setItem('username', loginForm.username)
        }
        ElMessage.success('登录成功！')
        router.push('/home')
      } else if (res1?.data?.message === '用户已登录') {
        // 如果已经登录了，判断一下浏览器保存的登录信息是不是再次登录的信息，如果是就正常登录
        const cookiesUsername = getUsername()
        if (cookiesUsername === loginForm.username) {
          ElMessage.success('登录成功！')
          router.push('/home')
        } else {
          ElMessage.warning('用户已在别处登录，请勿重复登录！')
        }
      } else {
        // B002000 为用户名或密码不匹配，其余错误直接展示后端返回的信息
        const message = res1?.data?.code === 'B002000'
          ? '用户名或密码错误'
          : res1?.data?.message
        ElMessage.error(message || '登录失败')
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || '登录失败，请检查后端服务是否已启动')
    }
  })
}

const loading = ref(false)
// 是否记住密码
const checked = ref(true)
// 动态波浪背景：跟随鼠标，离开页面时销毁
const vantaRef = ref()
let vantaEffect = null
onMounted(() => {
  vantaEffect = WAVES({
    el: vantaRef.value,
    THREE: THREE,
    mouseControls: true,
    touchControls: true,
    gyroControls: false,
    minHeight: 200.0,
    minWidth: 200.0,
    scale: 1.0,
    scaleMobile: 1.0
  })
})
onBeforeUnmount(() => {
  if (vantaEffect) {
    vantaEffect.destroy()
  }
})
// 展示登录还是展示注册
const isLogin = ref(true)
const moveRef = ref() // 切换按钮模块
const changeLogin = () => {
  isLogin.value = !isLogin.value
}
</script>

<style lang="less" scoped>
.login-box {
  width: 400px;
  position: relative;
  z-index: 1;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--sl-radius-lg);
  padding: 36px 32px 20px;
  box-sizing: border-box;
  background-color: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(6px);
  box-shadow: 0 18px 48px rgba(8, 26, 56, 0.22);

  h2 {
    font-size: 20px;
    font-weight: 600;
    color: var(--sl-text);
    width: 100%;
  }

  .login-sub {
    margin: 6px 0 24px;
    font-size: 13px;
    color: var(--sl-text-muted);
  }

  .el-form-item {
    margin-bottom: 18px;
  }

  .btn-gourp {
    margin-top: 8px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0;

    .el-button {
      width: 100px;
    }

    .remeber-password {
      left: 0;
    }
  }
}

/deep/ .el-form-item__content {
  margin-left: 0 !important;
}

.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: var(--sl-bg);
  background-image: radial-gradient(circle at 50% 0%, #eef2ff 0%, #f5f6f8 45%);
}

/* 波浪动画铺满整页，垫在表单卡片下面 */
.vanta {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 0;
}

.login-brand {
  position: relative;
  z-index: 1;
  margin-bottom: 20px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.4px;
  color: #ffffff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.logon {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hidden {
  display: none;
}

.move {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--sl-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  color: var(--sl-text-muted);
}

:deep(.el-input__suffix-inner) {
  width: 60px;
}

.second-font {
  margin-left: 13px;
}
</style>
