# 🎮 MCBot LoadTest - Minecraft 压力测试工具

一个基于 MCProtocolLib 的命令行工具，可自动获取服务器版本，按指定间隔向服务器注入**随机名字**的假人，用于压力测试。支持全版本，并在所有假人上线后提供交互命令，可查看在线玩家并指挥假人攻击指定目标

## ✨ 功能
- 自动识别任意 Minecraft 版本（1.8 到最新快照）
- 显示服务器在线人数、最大人数、版本号
- 假人名字完全随机生成（无任何前缀）
- 自定义上线间隔与假人总数
- 假人上线后进入命令模式，支持：
  - `list` - 列出所有假人
  - `players` - 查看当前在线玩家列表
  - `say <假人> <消息>` - 让假人发送聊天消息
  - `attack <假人> <玩家>` - 让假人持续攻击指定玩家
  - `stop <假人>` - 停止攻击
  - `exit` - 关闭全部假人并退出
- 单个 JAR 文件，双击/命令行即可运行

## ⚠️ 重要声明
**本工具仅限用于你自己拥有或授权的服务器！** 严禁对他人服务器进行压测或攻击，否则后果自负。

## 📥 下载
- **推荐：** 前往 [Actions](https://github.com/你的用户名/mc-loadtest/actions) 页面，选择最新的成功构建，下载 `MCBot-LoadTest` 压缩包，解压得到 `MCBot-LoadTest.jar`。
- 或前往 [Releases](https://github.com/你的用户名/mc-loadtest/releases) 手动下载发布版本。

## 🚀 使用
1. 确保你的服务器 `server.properties` 中 `online-mode=false`（关闭正版验证）。
2. 打开终端，运行：
   ```bash
   java -jar MCBot-LoadTest.jar
```

3. 按提示输入服务器 IP:端口、间隔秒数、假人总数。
4. 等待所有假人加入后，进入 > 命令模式，输入 help 查看可用命令。

示例：让假人攻击玩家

```
> attack x7k3m1 Steve
假人 x7k3m1 开始攻击 Steve
```

🛠️ 自行编译

```bash
git clone https://github.com/你的用户名/mc-loadtest.git
cd mc-loadtest
mvn clean package
```

生成的 JAR 在 target/MCBot-LoadTest.jar。

📦 依赖

· MCProtocolLib (Apache 2.0)

📄 许可证

MIT License
