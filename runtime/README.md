# Local Runtime Packages

本目录只保存本机开发使用的中间件包。除本说明外，二进制、安装包、数据和日志均不提交 Git。

运行包记录必须包含组件名称、准确版本、发布页、直接下载地址、SHA256、可执行文件绝对路径和手动启动命令。只有从本机下载文件计算出 SHA256 后才能写入对应记录，禁止预填或猜测哈希。

## 当前工程工具链与数据库基线

- Java 21：`D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk`
- Maven 3.9.1：`D:\ruanjian\apache-maven-3.9.1`；依赖仓库：`D:\project\MavenRepository391`
- 本机开发数据库：PostgreSQL 12.1，`127.0.0.1:5433/ai_learn`；SQL/Flyway 兼容性以下限 12.1 为准
- 本目录不提供数据库运行包；阶段 0-7 的本地练习与迁移验证直接使用本机 PostgreSQL 12.1（`127.0.0.1:5433/ai_learn`）

手动启动前先确认 6379 和 1883 未被其他进程占用。

6379 被占用时，不得直接复用既有 Redis；仅当用户先验证该实例只绑定 `127.0.0.1`/`::1`，且认证策略与项目配置一致时才可复用。当前观察到监听 `0.0.0.0/[::]:6379` 的 Redis 不符合该条件，不能直接复用；自动化不得停止既有进程。

## Redis 3.0.504

- 组件：Redis 3.0.504（Microsoft Archive Windows x64 发布包）
- 发布页：https://github.com/microsoftarchive/redis/releases/tag/win-3.0.504
- 直接下载地址：https://github.com/microsoftarchive/redis/releases/download/win-3.0.504/Redis-x64-3.0.504.zip
- ZIP 文件大小：5875707 字节
- SHA256：5F761367601CA31F6C8969E427CACC0DA4F428712954A66AAB303F83E390566E
- 可执行文件：`D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504\redis-server.exe`

手动启动命令：

```powershell
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504'
.\redis-server.exe '..\..\deploy\local\redis.conf'
```

本任务仅完成下载、解压和只读检查，未启动 Redis 服务。

## Mosquitto 2.1.2

- 组件：Mosquitto 2.1.2（Eclipse Mosquitto 官方 Windows x64 安装包）
- 发布页：https://mosquitto.org/download/
- 直接下载地址：https://mosquitto.org/files/binary/win64/mosquitto-2.1.2-install-windows-x64.exe
- 安装包文件大小：27068694 字节
- SHA256：58008AD7A22ADA0B4073AFA415801746E027C5F583E4FA52D0F4E9193B98D6AA
- 可执行文件：`D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto.exe`

手动启动命令：

```powershell
& 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto.exe' -c 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\deploy\local\mosquitto.conf' -v
```

本任务仅完成下载、静默安装和只读检查，未启动 Mosquitto Broker。

审计说明：未捕获安装程序退出码；已以官方 URL、SHA256、已安装的 `mosquitto.exe` 2.1.2、帮助命令退出码、忽略的 PATH 以及原服务 PID 未变化作为补偿验证，未重跑安装程序。

### 本项目 MQTT 运行初始化（不含真实密码）

IoT 真实消费默认关闭；启用前必须先在运行环境创建 Mosquitto 凭证文件和 ACL 文件，且两个文件不得加入 Git。设备用户名使用平台创建凭证返回的 `credential_reference`，IoT 服务消费使用独立的只读订阅账号，二者不能混用。

PowerShell 示例（占位符必须替换为本机安全路径和账号，不要把真实密码写入仓库）：

```powershell
$mqttPasswordFile = 'D:\secrets\mosquitto\passwordfile'
$mqttAclFile = 'D:\secrets\mosquitto\aclfile'
& 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto_passwd.exe' -c $mqttPasswordFile '<iot-subscription-account>'
& 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto_passwd.exe' $mqttPasswordFile '<credential_reference>'
```

`passwordfile` 创建后应由文件权限保护；`aclfile` 至少包含以下规则，并将 `<iot-subscription-account>` 替换为独立订阅账号：

```text
pattern write devices/%u/telemetry
user <iot-subscription-account>
topic read devices/#
```

在 `deploy/local/mosquitto.conf` 或 `deploy/docker/mosquitto.conf` 中启用与实际路径一致的 `password_file`、`acl_file`。IoT 服务配置示例：

```text
IOT_MQTT_ENABLED=true
IOT_MQTT_SERVER_URI=tcp://127.0.0.1:1883
IOT_MQTT_USERNAME=<iot-subscription-account>
IOT_MQTT_PASSWORD=<runtime-injected-secret>
IOT_MQTT_PASSWORD_FILE=<external-password-file>
IOT_MQTT_ACL_FILE=<external-acl-file>
```

设备仅可发布 `devices/{credential_reference}/telemetry`；模拟入口仍需显式开启对应应用能力。不要提交真实密码、生成的 `passwordfile`/`aclfile` 或包含密钥的日志。

## OpenJDK 21 (Eclipse Temurin)

- 组件：Eclipse Temurin OpenJDK 21.0.12.1+1（HotSpot Windows x64 发布包）
- 发布页：https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1
- 直接下载地址：https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip
- ZIP 文件大小：205073461 字节
- SHA256：F9D6E191AB098C0D416E7D588A24420A8621CD2F4720DAB2459B8B7B2D2D8B4E
- 可执行文件：`D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk\bin\java.exe`

验证命令：

```powershell
& 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk\bin\java.exe' -version
```
