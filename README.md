# ShortLink · 短链接平台

一个可商用的短链接（SaaS）系统实现：把长网址压缩成短码、提供跳转服务，并对每次访问做多维统计（PV / UV / UIP、地区、浏览器、系统、设备、网络、新老访客）。

> 前端基于 Vue 3 + Element Plus，后端基于 Spring Cloud Alibaba（Spring Boot 3），数据层使用 ShardingSphere 分库分表 + Redis + RocketMQ。

---

## 功能

### 短链接

- 创建 / 修改 / 删除短链接，支持自定义有效期、分组管理
- 短码使用 **MurmurHash64 + Base62**（最长 11 位）
- 跳转链路：Redis 缓存 → 布隆过滤器 → 分片路由表，附带空值缓存防穿透、分布式锁防击穿
- 回收站：逻辑删除后可恢复；**超过保留期（默认 30 天）由定时任务物理清理**（链接 + 路由 + 各统计表 + 访问日志）

### 监控统计

- 基础指标：PV / UV / UIP、今日数据、历史趋势
- 维度分布：地区、浏览器、操作系统、设备、访问网络、新老访客
- 访客识别使用 `uv` Cookie（UUID，30 天），**UV / UIP 采用双集合**：全局集合算累计，当日集合算今日
- **访问记录导出 Excel**（EasyExcel，分页拉取，单次上限 5 万行）
- **全站概览**：聚合当前账号下所有分组的访问数据

### 平台能力

- 用户注册 / 登录 / 修改密码（校验原密码）/ 退出所有设备
- 登录态基于 Redis Hash，Gateway 统一校验并滑动续期
- 分组管理（增删改查、排序）
- 统计消息走 RocketMQ，带幂等去重与**死信落库 + 手动重放**

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 / 构建 | Java 17、Maven |
| 框架 | Spring Boot 3.0.7、Spring Cloud 2022.0.3、Spring Cloud Alibaba 2022.0.0.0-RC2 |
| 注册配置 | Nacos 2.x |
| 网关 | Spring Cloud Gateway |
| 持久层 | MyBatis-Plus 3.5.3.1、ShardingSphere-JDBC 5.3.2 |
| 数据库 / 缓存 | MySQL 8（兼容 5.7）、Redis 7（Redisson） |
| 消息 | RocketMQ 5.x |
| 限流 / 熔断 | Sentinel |
| 工具 | Hutool、fastjson2、EasyExcel、jsoup（抓取网页标题/图标） |
| 前端 | Vue 3、Vite、Element Plus、ECharts、axios |

---

## 模块与端口

| 模块 | 端口 | 说明 |
|---|---|---|
| `gateway` | 8000 | 统一入口，路由 `/api/short-link/admin/**`，校验登录态 |
| `project` | 8001 | 短链接核心：跳转、统计、回收站、死信。同时暴露内部接口给 admin |
| `admin` | 8002 | 后台管理：用户、分组、短链管理、监控查询、导出 |
| `console-vue` | 5174 | 前端，构建后由 nginx 托管 |

服务间调用：`admin` 通过 OpenFeign 调 `project` 的内部接口，请求头带 `X-Internal-Token`；`project` 侧用过滤器拦截 `/api/**`，无 token 直接 403。

---

## 快速开始

### 1. 准备基础服务

| 服务 | 默认端口 | 备注 |
|---|---|---|
| MySQL | 3306 | 库名 `link` |
| Redis | 6379 | 建议设置密码 |
| Nacos | 8848 | 单机模式即可 |
| RocketMQ | 9876 / 10911 | NameServer + Broker；需要 topic `short-link-stats-topic` |
| Nginx | 5174 | 托管前端静态资源 |

### 2. 初始化数据库

创建库 `link`，然后按 `shardingsphere-config-{env}.yaml` 里的分片规则建表：

- `admin` 分片：`t_user_0..15`、`t_group_0..15`（按 `username` 哈希）
- `project` 分片：`t_link_0..15`、`t_link_goto_0..15`、`t_link_stats_today_0..15`（按 `gid` / `full_short_url`）
- 非分片表：`t_link_access_logs`、`t_link_access_stats`、`t_link_locale_stats`、`t_link_os_stats`、`t_link_browser_stats`、`t_link_device_stats`、`t_link_network_stats`、`t_link_dlq_message`

几个关键索引 / 约束：

```sql
-- 统计表按 链接+日期+小时 唯一，保证 upsert 累加而不是插入重复行
ALTER TABLE t_link_access_stats
  ADD UNIQUE KEY uk_url_gid_date_hour (full_short_url, gid, date, weekday, hour);

-- 访问日志按时间检索
ALTER TABLE t_link_access_logs
  ADD KEY idx_short_url_gid_time (full_short_url, gid, create_time),
  ADD KEY idx_gid_time (gid, create_time);

-- 扩容后的短码（11 位）
ALTER TABLE t_link_0 MODIFY COLUMN short_uri VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

### 3. 配置环境变量

**代码里不保存任何明文密码 / 密钥**，全部通过环境变量注入（IDEA：Run Configuration → Environment variables；服务器：`export` 或 systemd `Environment=`）。

| 变量 | admin | project | gateway | 说明 |
|---|:--:|:--:|:--:|---|
| `MYSQL_USERNAME` | ✅ | ✅ | | 数据库账号 |
| `MYSQL_PASSWORD` | ✅ | ✅ | | 数据库密码 |
| `REDIS_HOST` / `REDIS_PORT` | ✅ | ✅ | ✅ | 默认 `127.0.0.1` / `6379` |
| `REDIS_PASSWORD` | ✅ | ✅ | ✅ | Redis 密码 |
| `NACOS_SERVER_ADDR` | ✅ | ✅ | ✅ | 默认 `127.0.0.1:8848` |
| `INTERNAL_API_TOKEN` | ✅ | ✅ | | 服务间内部调用令牌，两端必须一致 |
| `AMAP_KEY` | | ✅ | | 高德开放平台 Key，用于 IP 归属地 |
| `ROCKETMQ_NAME_SERVER` | | ✅ | | 默认 `127.0.0.1:9876` |
| `SHORT_LINK_DOMAIN` | | ✅ | | 生成短链的域名，如 `127.0.0.1:8001` |
| `LOGIN_TOKEN_TTL_MINUTES` | ✅ | | ✅ | 登录态有效期，默认 30 |
| `SHARDINGSPHERE_CONFIG` | ✅ | ✅ | | 默认 `classpath:shardingsphere-config-dev.yaml` |
| `RECYCLE_BIN_*` | | ✅ | | 回收站清理开关 / 保留天数 / cron，均有默认值 |

前端只需要 `console-vue/.env.development` 里的 `VITE_SHORT_DOMAIN`。

> ShardingSphere 的 YAML 不解析 Spring 的 `${}` 占位符，所以 `ShardingSphereDataSourceConfiguration` 会先读出 YAML、手动 `resolvePlaceholders`，再交给 `YamlShardingSphereDataSourceFactory`。

### 4. 启动

```bash
# 1) 基础服务
nacos / mysql / redis / rocketmq

# 2) 后端（顺序无关，但建议先 project）
mvn -pl project -am spring-boot:run
mvn -pl admin   -am spring-boot:run
mvn -pl gateway -am spring-boot:run
```

### 5. 前端

```bash
cd console-vue
npm install
npm run build          # 产物在 dist/
# 拷贝 dist 到 nginx 的 html/dist-link，然后
nginx -s reload
```

开发模式：`npm run dev`（默认 5173），接口通过 Vite 代理到网关 8000。

---

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/short-link/admin/v1/user` | 注册 |
| POST | `/api/short-link/admin/v1/user/login` | 登录 |
| PUT | `/api/short-link/admin/v1/user/password` | 修改密码（校验原密码，改完踢下线） |
| DELETE | `/api/short-link/admin/v1/user/sessions` | 退出所有设备 |
| POST | `/api/short-link/admin/v1/link` | 创建短链接 |
| GET | `/api/short-link/admin/v1/link/page` | 短链接分页 |
| GET | `/api/short-link/admin/v1/stats` | 单条短链监控数据 |
| GET | `/api/short-link/admin/v1/stats/group` | 分组监控数据 |
| GET | `/api/short-link/admin/v1/stats/overview` | 全站总览 |
| GET | `/api/short-link/admin/v1/stats/export` | 导出访问记录 Excel |
| GET | `/api/short-link/admin/v1/mq/dlq` | 死信分页 |
| POST | `/api/short-link/admin/v1/mq/dlq/replay` | 重放死信 |
| GET | `/{短码}` | 短链接跳转（project 直接暴露） |

---





## License

仅用于学习与技术交流。
