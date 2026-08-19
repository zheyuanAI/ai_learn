# Local Runtime Packages

本目录只保存本机开发使用的中间件包。除本说明外，二进制、安装包、数据和日志均不提交 Git。

运行包记录必须包含组件名称、准确版本、发布页、直接下载地址、SHA256、可执行文件绝对路径和手动启动命令。只有从本机下载文件计算出 SHA256 后才能写入对应记录，禁止预填或猜测哈希。

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
