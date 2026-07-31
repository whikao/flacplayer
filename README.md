# FLAC 播放器（离线）

纯离线 Android 音乐播放器：Kotlin + Jetpack Compose + Media3 ExoPlayer（原生 FLAC 解码）。
**不申请 INTERNET 权限**，不做全盘扫描，只读取你手动选择的文件夹/文件，安全无网络风险。

## 功能
- SAF 手动导入：选一个文件夹（递归扫描其中音频，记住授权）或选多个文件
- 读取内嵌元数据（标题/歌手/专辑/时长/内嵌封面），无封面时回退同目录 folder.jpg/cover.jpg
- 歌单：新建/重命名/删除/加歌/移除（Room 本地存储）
- 播放：MediaSessionService 后台播放 + 通知栏控制、上一首/下一首、进度拖动、顺序/单曲循环/随机
- 睡眠定时：任意 1–999 分钟，显示剩余时间、可取消、到时自动暂停
- 歌词：同目录同名 .lrc 优先，其次内嵌歌词，逐行高亮滚动
- 正在播放页：封面大图 + 标题/歌手/专辑
- 深色低饱和配色（深炭灰 + 暖琥珀）

## 如何拿到 APK（三步）

1. **建仓库**：在 GitHub 新建一个公开仓库，把本项目全部文件（含 `.github` 目录、`gradlew`、`gradle/` 等）原样上传。
2. **运行构建**：打开仓库的 **Actions** 页签，选择左侧 **Build Release APK**，点击 **Run workflow** 手动触发。
3. **下载安装**：构建完成（约 5–10 分钟）后，在该次运行页面底部的 **Artifacts** 下载 `FLAC-Player-APK.zip`，解压得到 `app-release.apk`，传到手机安装即可。

## 关于签名（说明）

APK 由 CI 在构建时用 `keytool` 临时生成自签名证书完成签名（别名 `flacplayer`，固定口令仅存在于该次 CI 运行环境中，构建结束即销毁）。
该证书仅用于让你本人安装应用；应用**没有任何网络权限**，无法联网，天然安全。

## 本地构建（可选）

需要 JDK 17 + Android SDK 34：

```bash
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 发布包（需设置 KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD 环境变量）
```

## 权限清单
| 权限 | 用途 |
| --- | --- |
| READ_MEDIA_AUDIO (API33+) / READ_EXTERNAL_STORAGE (≤32) | 读取用户选择的音频文件 |
| POST_NOTIFICATIONS | 播放通知栏控制器 |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK | 后台播放 |

**无 INTERNET 权限。**
