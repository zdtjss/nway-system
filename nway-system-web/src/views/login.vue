<template>
    <div class="login_bg">
        <div class="login_area">
            <div class="err_msg">{{ errMsg }}</div>
            <div style="margin-bottom: 20px;font-size: 30px;margin-top: -30px;">
                统一工作台
            </div>
            <div v-if="loginType == 'domain'">
                <div style="margin-right: 32px;">
                    <i class="icon32 icon_user icon_login"></i>
                    <input id="loginName" v-model="loginName" class="input" autofocus>
                </div>
                <div style="margin-right: 32px;">
                    <i class="icon32 icon_pwd icon_login"></i>
                    <input v-model="password" class="input" type="password" @keyup.enter="login('password')">
                </div>
                <div style="display: inline-block;text-align: center;width: 100%;padding-left: 50px;">
                    <div @click="login('password')" style="display: inline-block;cursor: pointer;font-size: 26px;">登录
                    </div>
                    <div @click="handleForgotPassword" style="display: inline-block;margin-left: 20px;margin-top: 13px;cursor: pointer;">忘记密码</div>
                </div>
            </div>
            <div v-if="loginType == 'bio'">
                <div style="min-height: 60px;">
                    <!-- <img :src="avatar" width="128" style="border-radius: 15px;"> -->
                    <i class="avatar avatar_login"></i>
                </div>
                <div style="display: inline-block;text-align: center;width: 100%;margin-top: 13px;">
                    <div @click="login('bio')" style="display: inline-block;cursor: pointer;font-size: 26px;">登录</div>
                </div>
            </div>
            <div style="position: absolute;bottom: 20px;right: 30px;">
                <span @click="loginType = 'domain'" v-if="loginType !== 'domain'" style="cursor: pointer;">账号密码</span>
                <span @click="loginType = 'bio'" v-if="loginType !== 'bio'"
                    style="margin-left: 20px;margin-top: 13px;cursor: pointer;">生物识别</span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { client, parsers, server } from '@passwordless-id/webauthn'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'

const route = useRoute()
const router = useRouter()

const loginType = ref('bio')
const loginName = ref('')
const password = ref('')
const avatar = ref("")
const errMsg = ref("")

const bioLocalStorageKey = "nway_system_bio_credential_id"

onMounted(() => {
    avatar.value = auth.getAvatar()
    const credentialId = window.localStorage.getItem(bioLocalStorageKey)
    if (credentialId && client.isAvailable()) {
        loginType.value = 'bio'
        nextTick(async () => {
            try {
                await login('bio')
            } catch (e) {
                // 静默失败
            }
        })
    } else {
        loginType.value = 'domain'
    }
})

watch(() => loginType.value, (newVal) => {
    if (newVal === 'domain') {
        nextTick(() => {
            document.getElementById("loginName").focus()
        })
    }
})

function login(type) {
    if ("password" === type) {
        loginPwd()
    } else if ("bio" === type) {
        loginBio()
    }
}

function handleForgotPassword() {
    ElMessage.info('请联系管理员重置密码')
}

function loginPwd() {
    const params = {
        name: loginName.value,
        key: password.value,
        clientType: "pc",
        loginType: "password"
    }
    http.post("/login", params).then(({ data }) => {
        if (data.code === 200) {
            afterLogin(data.data)
        } else {
            handleLoginError(data)
        }
    }).catch((error) => {
        errMsg.value = error.message || '登录失败';
    });
}

/** 密码过期 / 首次登录需改密：直接引导自助改密，避免用户卡在登录页 */
const PASSWORD_CHANGE_CODES = [1013, 1014]

function handleLoginError(data) {
    if (PASSWORD_CHANGE_CODES.includes(data.code)) {
        promptExpiredPassword()
        return
    }
    errMsg.value = data.msg
}

function promptExpiredPassword() {
    ElMessageBox.prompt('密码已过期或需先修改初始密码，请设置新密码（至少 8 位，需含大写字母、小写字母和数字）',
        '修改密码', {
        confirmButtonText: '确定修改',
        cancelButtonText: '取消',
        inputType: 'password',
        inputValidator: (value) => {
            if (!value || value.length < 8) return '密码长度不能少于 8 位'
            if (!/[A-Z]/.test(value)) return '密码必须包含大写字母'
            if (!/[a-z]/.test(value)) return '密码必须包含小写字母'
            if (!/\d/.test(value)) return '密码必须包含数字'
            return true
        }
    }).then(({ value }) => {
        http.post('/login/external/changePassword', {
            username: loginName.value,
            oldPassword: password.value,
            newPassword: value
        }).then(({ data }) => {
            if (data.code === 200) {
                password.value = ''
                errMsg.value = ''
                ElMessage.success('密码修改成功，请使用新密码登录')
            } else {
                errMsg.value = data.msg || '密码修改失败'
            }
        }).catch((error) => {
            errMsg.value = error.message || '密码修改失败'
        })
    }).catch(() => {
        errMsg.value = '请完成密码修改后再登录'
    })
}

async function loginBio() {
    const credential = window.localStorage.getItem(bioLocalStorageKey)
    // 兼容旧版直接存储的裸 credentialId（非 JSON），统一按 { id, transports } 处理
    let allowCredential = null
    if (credential) {
        try {
            allowCredential = JSON.parse(credential)
        } catch (e) {
            allowCredential = { id: credential, transports: [] }
        }
    }
    // 第一个参数指定值，可以简化用户选择的操作
    let authentication = await client.authenticate({
        allowCredentials: allowCredential == null ? [] : [allowCredential],
        challenge: server.randomChallenge()
    })

    const authenticationParsed = await parsers.parseAuthentication(authentication);

    http.post("/login", {
        key: authenticationParsed.credentialId,
        clientType: "pc",
        loginType: "bio"
    }).then(({ data }) => {
        if (data.code === 200) {
            if (authenticationParsed.credentialId) {
                // 与注册时保持一致：以 JSON 形式存储，避免下次登录 JSON.parse 失败
                window.localStorage.setItem(bioLocalStorageKey, JSON.stringify({ id: authenticationParsed.credentialId, transports: [] }))
            }
            afterLogin(data.data)
        } else {
            handleLoginError(data)
        }
    }).catch((error) => {
        errMsg.value = error.message || '登录失败'
    });
}

/**
 * 解析登录后的回跳地址（旧书签 / login-web 跳转壳会携带 redirect）。
 * 仅允许同源或公司内可信域名，避免开放重定向。
 */
function resolveRedirect() {
    const redirect = route.query.redirect
    if (!redirect || typeof redirect !== 'string') return ''
    try {
        const target = new URL(redirect, window.location.origin)
        const trusted = ['nway.net.cn', 'nway.com', 'nway.cn']
        const sameOrigin = target.origin === window.location.origin
        const trustedHost = trusted.some(h => target.hostname === h || target.hostname.endsWith('.' + h))
        const devLocal = import.meta.env.DEV && /^(localhost|127\.0\.0\.1)$/.test(target.hostname)
        return sameOrigin || trustedHost || devLocal ? target.href : ''
    } catch (e) {
        return ''
    }
}

/**
 * 登录成功后的统一处理：保存登录态；若服务端标记需先改密，则强制引导修改初始密码。
 */
function afterLogin(loginUser) {
    auth.saveUser(loginUser)
    auth.saveToken(loginUser.token)
    if (loginUser.pwdResetRequired) {
        promptChangeInitialPassword()
        return
    }
    const redirect = resolveRedirect()
    if (redirect) {
        const target = new URL(redirect)
        // 跨域子应用无法读取本域 sessionStorage：一次性通过 URL 传递 token，
        // 子应用读取后会立即从地址栏移除（见 app-web utils/hostAuth.js）
        if (target.origin !== window.location.origin) {
            target.searchParams.set('ds_token', loginUser.token)
        }
        window.location.replace(target.href)
        return
    }
    router.push("/")
}

/**
 * 强制修改初始密码（新建用户/管理员重置密码后首次登录）。
 * 改密成功后服务端会作废当前 Token，需用新密码重新登录。
 */
function promptChangeInitialPassword() {
    ElMessageBox.prompt('为保证账号安全，请先设置新密码（至少 8 位，需含大写字母、小写字母和数字）',
        '请修改初始密码', {
        confirmButtonText: '确定修改',
        showCancelButton: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showClose: false,
        inputType: 'password',
        inputValidator: (value) => {
            if (!value || value.length < 8) return '密码长度不能少于 8 位'
            if (!/[A-Z]/.test(value)) return '密码必须包含大写字母'
            if (!/[a-z]/.test(value)) return '密码必须包含小写字母'
            if (!/\d/.test(value)) return '密码必须包含数字'
            return true
        }
    }).then(({ value }) => {
        http.post("/login/resetPassword", null, { params: { password: value } }).then(({ data }) => {
            if (data.code === 200) {
                auth.clearUser()
                errMsg.value = ''
                ElMessage.success('密码修改成功，请使用新密码重新登录')
            } else {
                errMsg.value = data.msg || '密码修改失败'
            }
        }).catch((error) => {
            errMsg.value = error.message || '密码修改失败'
        })
    }).catch(() => {
        // 用户中断改密：保留登录态，但后端会持续拒绝业务接口，直至完成改密
        errMsg.value = '请完成初始密码修改后再使用系统'
    })
}
</script>

<style>
.login_area {
    border: 1px solid;
    display: flex;
    -webkit-box-align: center;
    align-items: center;
    -webkit-box-pack: center;
    justify-content: center;
    -webkit-box-orient: vertical;
    -webkit-box-direction: normal;
    flex-direction: column;
    position: absolute;
    top: 50%;
    left: 50%;
    width: 350px;
    height: 370px;
    margin-top: -185px;
    margin-left: -175px;
    background: hsla(0, 0%, 100%, .75);
    -webkit-box-shadow: 0 3px 9px 0 hsla(0, 0%, 63.1%, .5);
    box-shadow: 0 3px 9px 0 hsla(0, 0%, 63.1%, .5);
    border-radius: 14px;
    -webkit-transition: background 1.5s ease 0s;
    transition: background 1.5s ease 0s
}

.input {
    font-size: 22px;
    width: 180px;
    height: 46px;
    line-height: 46px;
    border: 1px solid;
    border-radius: 8px;
    margin-bottom: 10px;
    outline: 0;
    padding: 0 15px 0px 50px;
}

.icon_login {
    position: relative;
    left: 37px;
    top: 9px;
}

.avatar_login {
    border-radius: 10px !important;
    width: 128px !important;
    height: 120px !important;
    background-size: 128px !important;
}

.err_msg {
    position: absolute;
    top: 10px;
    right: 15px;
    color: red;
}
</style>