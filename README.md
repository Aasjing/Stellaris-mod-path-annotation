# Stellaris Mod Manager - IntelliJ IDEA Plugin

一个用于管理和浏览 Steam Workshop 中 Stellaris 模组的 IntelliJ IDEA 插件。

## 功能特性

- 自动检测 Steam Workshop 目录（支持 Windows / macOS / Linux）
- Windows 下自动解析 `libraryfolders.vdf`，支持多 Steam 库路径
- 读取 `descriptor.mod` 文件提取模组信息（名称、版本、支持版本）
- 颜色编码显示模组信息
  - 文件夹名：白色
  - 模组名称：棕色
  - 版本号：灰白色
  - 支持版本：绿色
- 点击模组直接在 IDE 中打开项目
- 异步加载，不阻塞 UI
- 自动检测缩略图（`thumbnail.png` / `thumb.png` / `preview.png` 等），鼠标悬浮时左侧弹出预览
- 列表 / 图标双视图（默认图标视图），工具栏切换按钮
- 图标视图自适应行列布局，缩略图 128x128 等比缩放居中
- 拖拽重排：拖拽手柄拖动整行 / 图标卡片，蓝色高亮标记插入位置
- 上下箭头按钮辅助排序
- 自动排序：按名称 / 按大小 / 按修改时间
- 一键刷新恢复原始顺序

## 构建方法

```bash
./gradlew buildPlugin
```

构建后的插件位于 `build/distributions/` 目录下。

## 安装方法

1. 打开 IntelliJ IDEA
2. 进入 `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. 选择生成的 `.zip` 文件
4. 重启 IDE

## 使用方法

1. 打开右侧工具栏中的 "Stellaris mod 路径标注："
2. 插件自动扫描 Steam Workshop 目录并以图标视图展示
3. 鼠标悬浮在卡片或列表项上可预览缩略图
4. 工具栏按钮：刷新 / 列表-图标视图切换 / 排序（名称、大小、时间）
5. 拖拽手柄或缩略图区域可拖拽重排，上下箭头按钮逐位移动
6. 点击任意模组可在 IDE 中打开该项目

## 系统要求

- IntelliJ IDEA 2025.1 或更高版本
- Java 21 或更高版本
- Steam 客户端（已安装 Stellaris 并订阅过 Workshop 模组）

## 许可证

MIT License
